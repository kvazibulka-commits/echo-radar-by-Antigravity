# ЭХО — состояние проекта

> Этот файл — долговременная память проекта. Если контекст сессии сжался или
> началась новая сессия, читай сначала `docs/PROJECT_STATE.md`, затем
> `skills/*/SKILL.md` и `CHANGELOG.md`. Здесь зафиксировано то, что нельзя
> восстановить из кода: решения, договорённости, текущие версии и открытые задачи.

**Обновлено:** 2026-11-12 · **Версия:** 3.3.0 (Android 0.3.0-alpha)

## Что это

ЭХО — ночной музыкальный сонар: показывает не людей рядом, а музыку, которая
сейчас играет вокруг, и позволяет тихо откликнуться. Сервер на Python stdlib +
SQLite, два веб-клиента (пульт и PWA-сонар), Android-клиент на Compose, ПК-узел
через Windows SMTC, публичный туннель Cloudflare.

Репозиторий: https://github.com/kvazibulka-commits/echo-radar-by-Antigravity

## Текущие версии

| Компонент | Версия | Где |
|---|---|---|
| Сервер | 3.3.0 | `echo_server.py`, `SERVER_VERSION` |
| Android | 0.3.0-alpha (versionCode 5) | `app/build.gradle.kts` |
| API | без изменений с 3.1.0 | `README.md`, таблица API |
| Релизы | v3.3.0 (APK) | GitHub Releases |

## Как запускать

```bat
python echo_server.py                  :: http://127.0.0.1:8080 и /app
python echo_pc.py                      :: музыка Windows в эфир
python echo_tunnel.py                  :: публичный HTTPS

python -m unittest discover -s tests -v
python tools/smoke_test.py
python tools/cdp_shot.py --url http://127.0.0.1:8080/app --out shot.png --mobile
```

Важно: локального Android SDK/JDK нет. Kotlin проверяется только сборкой в
GitHub Actions (`python deploy_build.py`). Если сборка упала — смотри лог run
через API и чини, затем пуш снова.

## Как выпускать

```bat
python deploy_build.py --message "feat: ..."        :: push + CI + APK
python tools/publish_release.py --tag vX.Y.Z --body-file notes.md
```

- Токен GitHub — `deploy_token.txt` (gitignored) или `GITHUB_TOKEN`.
- `deploy_build.py` публикует только файлы из `FILES_TO_COMMIT`; **новый файл
  обязательно добавь туда**, иначе он не попадёт в репозиторий.
- Релиз создаётся один раз на тег; повторный запуск безопасен.
- После сборки APK лежит в `app-debug.apk`, digest сверяется с ассетом релиза.

## Архитектура (кратко)

- `echo_server.py` — HTTP API, SQLite WAL, состояние эфира, архетипы, резонанс.
  Авторизация: LAN/loopback без токена; всё, что пришло через proxy-заголовки
  (`CF-Connecting-IP`, `CF-Ray`, `X-Forwarded-For`), требует токен.
- `templates/dashboard.html` — пульт: живой эфир, отклики, плейлист, тайники.
- `templates/pwa.html` — сонар/карта/эфир/тайники, deep links `#air` и т.д.
- Android: `EchoStateRepository` (StateFlow + поллинг), `EchoMediaListenerService`
  (медиа-сессии + уведомления + мгновенные callbacks), `MainActivity` (4 экрана,
  шторки), `EchoAudioPlayer` (превью 30 с), `EchoChronicleManager`, `EchoBleManager`,
  `EchoWidgetProvider` (виджет «Сейчас играет» через SharedPreferences + RemoteViews).
- Скилы: `skills/echo-product-concept`, `echo-visual-design`, `echo-engineering`,
  `echo-quality`; установка — `python tools/install_skills.py --force`.

## Решения, которые нельзя потерять

1. **Туннель ≠ LAN.** Любой proxy-заголовок делает запрос внешним и требует токен.
2. **Секреты только вне кода.** `echo_config.json`, `deploy_token.txt`, `.db`, `.apk`
   в `.gitignore`; в репозитории их нет.
3. **Текст с границы** проходит `sanitize_text`, `is_junk_track`, `normalize_zone`.
   Служебные вкладки браузеров не попадают в эфир.
4. **Клиент не считает резонанс** — только сервер; клиенты отображают.
5. **Дизайн:** сигнальный циан `#00F0FF` только для «живого», рабочий акцент
   `#38BDF8`. Токены — в `EchoTokens` и обоих `:root`.
6. **Права:** доступ к уведомлениям нужен для Яндекс Музыки; геолокация —
   для поиска рядом. Оба статуса обновляются в `onResume`.
7. **Медиа-сессии:** опрос раз в 2.5 с + `EXTRA_MEDIA_SESSION`; allowlist пакетов,
   без широкого `yandex`, чтобы не ловить Яндекс Карты.
8. **Отклики:** публичные цели (`sig_*`, `cap_*`, `broadcast`) видны всем;
   личные — только адресату; пульт видит всё.
9. **Превью:** таймаут 12 с; без превью кнопка ведёт в Яндекс Музыку.

## Что сделано в 3.2–3.3

- 3.2.0: перехват Яндекс Музыки, «Поделиться → ЭХО», ручная трансляция.
- 3.2.1: пустая дека открывает приложение Яндекс Музыки, а не поиск.
- 3.2.2: геоплашка исчезает после выдачи доступа; «ЗАГРУЗКА...» больше не
  зависает; отклики с ПК доходят до телефона.
- 3.3.0: домашний виджет «Сейчас играет»; мгновенные `MediaController.Callback`
  вместо ожидания опроса.

## Открытые задачи / идеи

- Автостарт трансляции при старте воспроизведения без ожидания опроса
  (сейчас уже почти мгновенно за счёт callbacks — проверить на реальном устройстве).
- Реальная геолокация вместо зашитых зон Москвы.
- OAuth Яндекс Музыки (публичного API нет; сейчас только медиа-сессия и share).
- Экспорт плейлиста в Яндекс Музыку (сейчас — ссылки на поиск).
- Скриншоты Android-экранов и виджета в `docs/screenshots` (нет эмулятора локально).

## Известные ограничения

- Gemini из РФ отвечает `User location is not supported`/429 — работает
  локальная библиотека архетипов; это штатный фоллбэк.
- APK подписан debug-ключом.
- Без доступа к уведомлениям авто-режим не работает; ручной ввод и share — работают.
- Ссылку Яндекс Музыки без названия трека разобрать нельзя (oEmbed недоступен).

## Что читать дальше

1. `skills/echo-engineering/SKILL.md` — архитектура, API, правила Android.
2. `skills/echo-visual-design/SKILL.md` — токены, сонар, компоненты.
3. `skills/echo-quality/SKILL.md` — безопасность, DoD, анти-паттерны.
4. `skills/echo-product-concept/SKILL.md` — язык, тон, границы продукта.
5. `CHANGELOG.md` — что и когда менялось.
