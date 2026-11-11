---
name: echo-engineering
description: >-
  Разработка ЭХО (Echo Radar): карта репозитория, архитектура сервера и клиентов,
  контракт HTTP API, модель авторизации LAN/туннель, жизненный цикл сигналов и
  тайников, работа с SQLite и потоками, правила веб-клиентов и Android, сборка,
  деплой и запуск тестов. Загружай перед любой правкой кода сервера, шаблонов,
  Android-клиента, перед добавлением эндпоинта, миграцией базы или деплоем.
  Use for Echo architecture, implementation, API changes, debugging and deployment.
---

# ЭХО — разработка и архитектура

## Когда использовать

- Меняешь `echo_server.py`, `templates/*.html`, Kotlin-клиент, скрипты деплоя.
- Добавляешь эндпоинт, поле в БД, новую сущность состояния.
- Разбираешь баг: 401, пустой сонар, зависший поллинг, «призрак» вместо сигнала.
- Собираешь APK и публикуешь сборку.

## Карта репозитория

```
echo_server.py                  # HTTP API, SQLite, состояние эфира, 1 файл — 2100 строк
echo_pc.py                      # ПК-узел: Windows SMTC → POST /api/signal
echo_tunnel.py                  # Cloudflare quick tunnel + сохранение адреса в конфиг
echo_archetypes.json            # 93 архетипа + 22 жанровых кластера
echo_config.json                # секреты (gitignored): gemini_api_key, echo_token
templates/dashboard.html        # пульт: живой эфир, отклики, плейлист, токен
templates/pwa.html              # сонар/карта/эфир/тайники, mobile-first
app/src/main/java/ru/echo/radar # Android: Compose UI + сеть + медиа-сессии
tests/test_echo_server.py       # 20 юнит-тестов на временной БД
tools/smoke_test.py             # 27 проверок запущенного сервера
tools/cdp_shot.py               # CDP-скриншоты и ошибки JS
tools/publish_release.py        # GitHub Release + APK asset
deploy_build.py                 # Git Data API → Actions → скачивание APK
```

## Поток данных

```
Android (NotificationListener/MediaSession) ─┐
Windows SMTC (echo_pc.py или сервер) ────────┼→ POST /api/signal
Пульт (форма «Трансляция с ПК») ─────────────┘        │
                                                      ▼
                                   generate_curated_persona()
                                   (archetypes → Gemini → fallback)
                                                      │
                                                      ▼
                                   SQLite devices / listening_history
                                                      │
                        GET /api/state ◄───────────────┤
                                                      │
                       сонар, карта, пульт, Android ◄─┘
```

Клиент **никогда** не считает резонанс сам: сервер отдаёт `compatibilityPercent`,
`bpm`, `camelotKey`, `genre`, `resonanceStory`. Меняешь формулу — меняй только
`calculate_*` в `echo_server.py`; клиенты подхватят.

## API-контракт

| Метод | Путь | Авторизация | Тело / параметры | Ответ |
|---|---|---|---|---|
| GET | `/api/health` | публично | — | `status, version, uptimeSec, hasAi` |
| GET | `/api/state?deviceId=` | токен вне LAN | — | полное состояние + `serverMeta` |
| GET | `/api/playlist?limit=` | токен вне LAN | `limit` 1–60 | `tracks[]`, `text` |
| GET | `/api/cover/<id>` | публично | — | image/jpeg, image/png |
| POST | `/api/signal` | токен вне LAN | `id, artist, title, sourceName, zoneName, coverBase64?, recentTracks?` | состояние |
| POST | `/api/react` | токен вне LAN | `targetId, senderId, symbol, message?` | состояние |
| POST | `/api/needle_sync` | токен вне LAN | `targetId, senderId` | `{synced:true}` |
| POST | `/api/send_warmth` | токен вне LAN | `targetId, senderId, x, y` | `{patchId}` |
| POST | `/api/wave_impulse` | токен вне LAN | `targetId, senderId` | `{status}` |
| POST | `/api/drop_capsule` | токен вне LAN | `creatorId, artist, title, zoneName, secretMessage, isVenue?, venueName?, minResonance?, secretPerk?` | состояние |
| POST | `/api/delete_signal` | токен вне LAN | `targetId` | состояние |
| POST | `/api/delete_capsule` | токен вне LAN | `targetId` | `{deleted, state}` |
| POST | `/api/bump_encounter` | токен вне LAN | `targetId` | состояние |
| POST | `/api/save_config` | токен вне LAN | `gemini_api_key` | `{has_key}` |

Все POST возвращают `application/json`, `Cache-Control: no-store`. Неизвестный путь —
404 JSON, слишком большое тело — 413, лимит — 429.

## Авторизация: LAN ≠ туннель

Инвариант, который нельзя ломать:

```python
def _is_local_client(self):
    if self._is_proxied():      # CF-Connecting-IP, CF-Ray, X-Forwarded-For, ...
        return False            # туннель — всегда внешний
    return addr.is_loopback or addr.is_private
```

- `cloudflared` подключается к серверу с `127.0.0.1`. Если проверять только IP,
  весь интернет пройдёт без токена. Это уже было багом — не повторяй.
- Внешний клиент обязан прислать `X-Echo-Token`, `X-API-Key`, cookie `echo_token`
  или `?token=`.
- Cookie ставится на `/` и `/app`: `HttpOnly; SameSite=Lax; Secure` при HTTPS.
- Токен **не** попадает в `serverMeta` для внешних клиентов; локальному пульту —
  можно (`include_token=True`).
- Новый мутирующий эндпоинт обязательно добавь в `PROTECTED_PATHS`.

## Модель данных

| Таблица | Ключевые поля | Жизненный цикл |
|---|---|---|
| `devices` | `id, artist, title, persona_*, mood_label, color_hex, zone_name, distance_*, compatibility_percent, encounters_count, reactions_json, lat, lon, updated_at` | обычный сигнал удаляется через 90 мин; `sig_*` — вечные маяки, обновляются раз в 4 мин; `isGhost`, если возраст > 5 мин |
| `capsules` | `id, creator_id, artist, title, zone_name, secret_message, is_venue, venue_name, min_resonance, secret_perk, lat, lon` | живут до удаления; звуковой замок считается по `minResonance` |
| `listening_history` | `device_id, artist, title, persona_title, zone_name, played_at, timestamp` | дедуп по последнему треку; обрезается до 500 строк |

- Координаты — только через `get_zone_jittered_coords(zone, seed)`, чтобы не
  раскрывать точное место. Не пиши в БД реальные координаты пользователя.
- Зоны — только из `ZONE_COORDINATES`; вход нормализуй `normalize_zone()`.
- Текст с границы — `sanitize_text(..., MAX_TEXT_LEN)`; мусор — `is_junk_track()`.

## Резонанс

```
R_AB = 100 · [ B_AB + 0.25·(1 − B_AB)·S_AB ]
B_AB = 0.15·K + 0.10·T + 0.25·W + 0.15·M + 0.35·E
```

- `K` — Camelot (кольцо 12, соседние тональности), `T` — октавный темп,
  `M` — valence/energy, `S` — культурный мост, `W` — фиксированный вес 0.84.
- Итог ограничен 58–99.
- Тесты на границы лежат в `tests/test_echo_server.py`; меняешь формулу — обнови тесты.

## Конкурентность и производительность

- Одно соединение SQLite на запрос, `timeout=10`, WAL, `synchronous=NORMAL`.
- Все записи — под `DB_LOCK`; отклики — под `REACTIONS_LOCK`.
- Обложки — `LRUCache(MAX_COVERS)`, не словарь без предела.
- ID событий — `next_event_id()`, а не `int(time.time()*1000)` (коллизии).
- Поллинг клиентов: 2.5 с, при ошибках — 7 с; скрытая вкладка — пауза.
- `/api/state` — самый частый запрос: не добавляй в него тяжёлые синхронные вызовы
  (сеть, Gemini, файлы). Всё внешнее — через `_PERSONA_EXECUTOR` с таймаутом.
- Шаблоны кэшируются по `mtime`; правки видны без рестарта.

## Веб-клиенты: правила

- Никаких фреймворков и сборки: один HTML + inline CSS/JS. CSP разрешает
  `'self'`, `'unsafe-inline'` и `https://unpkg.com` (Leaflet).
- Любые данные сервера в HTML — только через `esc()`. Никогда не вставляй JSON в
  `onclick`, не собирай `innerHTML` из пользовательских строк.
- Действия — делегирование: `data-action`, `data-id`, один слушатель на документ.
- Deep links PWA: `#sonar`, `#map`, `#air`, `#caches`, `#settings`, `#playlist`.
- Токен — `localStorage.echo_token`, зона — `localStorage.echo_zone`; отправляй
  `X-Echo-Token`, если он есть.
- Скачивание плейлиста — Blob + `<a download>`; копирование — `navigator.clipboard`
  с fallback на `textarea`.
- Не блокируй первый рендер внешними шрифтами и картинками.

## Android: правила

- Единственный источник состояния — `EchoStateRepository` (`StateFlow`), экраны
  подписываются через `collectAsState()`.
- Сеть — OkHttp + корутины, `Dispatchers.IO`; таймауты 5–8 с.
- Перехват музыки — `EchoMediaListenerService` (NotificationListener + MediaSession).
- ПК/телефон не должны дублировать один `deviceId`; `pc_studio_node` — только ПК.
- Токен и адрес — в `SharedPreferences`; дефолтного токена в коде быть не должно.
- Новые строки интерфейса — на русском, в тоне ЭХО (см. `echo-product-concept`).
- **Локальной сборки нет** (нет JDK/Android SDK). Любая Kotlin-правка проверяется
  только через GitHub Actions: `python deploy_build.py`. Держи изменения маленькими.

## Запуск и проверка

```bat
python echo_server.py                 :: http://127.0.0.1:8080, /app
python echo_pc.py                     :: ПК-узел (SMTC) или --manual
python echo_tunnel.py                 :: публичный HTTPS

python -m unittest discover -s tests -v      :: юнит-тесты
python tools/smoke_test.py                   :: 27 проверок API и авторизации
python tools/cdp_shot.py --url http://127.0.0.1:8080/app --out shot.png --mobile
```

Переменные окружения: `PORT`, `ECHO_TOKEN`, `ECHO_DB_PATH`, `ECHO_CONFIG_PATH`,
`ECHO_DISABLE_SMTC`, `GEMINI_API_KEY`, `GITHUB_TOKEN`.

## Деплой

```bat
python deploy_build.py --message "feat: ..."          :: push + Actions + APK
python tools/publish_release.py --tag v3.2.0          :: релиз с APK
```

- Токен GitHub — только `GITHUB_TOKEN` или `deploy_token.txt` (gitignored).
- `deploy_build.py` публикует список файлов из `FILES_TO_COMMIT`; новый файл внеси
  туда, иначе он не попадёт в репозиторий.
- Сетевые сбои GitHub ретраятся 3 раза; повторный запуск безопасен.
- После сборки APK скачивается в `app-debug.apk`.

## Грабли, на которые уже наступали

| Симптом | Причина | Лечение |
|---|---|---|
| 401 у всех через туннель | `_is_local_client` верил `127.0.0.1` | проверка proxy-заголовков |
| Весь `/api/state` падает | `mood_label = NULL` | `(mood_label or "")` |
| В эфире вкладка браузера | SMTC без фильтра | `is_junk_track()` |
| `NameError: sys` | забыт импорт в `handle_error` | импортировать `sys` |
| Токен в APK | константа в Kotlin | хранить в prefs, вводить вручную |
| Пустой экран после смены трека | исключение в JS | `tools/cdp_shot.py` ловит ошибки |
| Дубли сигналов с ПК | сервер и `echo_pc.py` одновременно | `ECHO_DISABLE_SMTC=1` |
| Сборка CI падает | синтаксис Kotlin | проверить через `deploy_build.py` |

## Чек-лист перед коммитом

1. `python -m py_compile` для всех изменённых `.py`.
2. `python -m unittest discover -s tests` — зелёный.
3. Сервер поднят, `python tools/smoke_test.py` — 0 провалов.
4. Изменённый экран снят `cdp_shot.py` на 390 px, ошибок JS нет.
5. Новый эндпоинт в `PROTECTED_PATHS` и в README-таблице API.
6. Новый файл добавлен в `FILES_TO_COMMIT`.
7. Секреты не попали в дифф (`echo_config.json`, `deploy_token.txt`, `.db`, `.apk`).
8. `CHANGELOG.md` и при необходимости `README.md` обновлены.
