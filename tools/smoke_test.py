#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ЭХО — дымовой тест запущенного сервера (tools/smoke_test.py).

Проверяет доступность, авторизацию публичного туннеля, защитные заголовки,
фильтр служебных треков и плейлист. Запускается против работающего
echo_server.py:

    python tools/smoke_test.py
    python tools/smoke_test.py --base http://127.0.0.1:8080 --token <token>

Код возврата 1, если хотя бы одна проверка провалилась.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROXY_HEADERS = {"CF-Connecting-IP": "203.0.113.7", "CF-Ray": "smoke-test"}
JUNK_TRACK = {"artist": "", "title": "chrome-extension://abc/offscreen.html"}

PASSED = 0
FAILED = 0


def request(base: str, path: str, method: str = "GET", payload=None, headers=None):
    url = base.rstrip("/") + path
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    all_headers = {"Accept": "application/json", "User-Agent": "EchoSmoke/1.0"}
    if data is not None:
        all_headers["Content-Type"] = "application/json"
    all_headers.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=all_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            return resp.status, dict(resp.headers), raw
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers), exc.read()
    except urllib.error.URLError as exc:
        print(f"[!] Сервер недоступен: {exc}")
        sys.exit(2)


def check(name: str, condition: bool, detail: str = "") -> None:
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  ✅ {name}")
    else:
        FAILED += 1
        print(f"  ❌ {name}" + (f" — {detail}" if detail else ""))


def as_json(raw: bytes) -> dict:
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return {}


def main() -> int:
    parser = argparse.ArgumentParser(description="Дымовой тест сервера ЭХО")
    parser.add_argument("--base", default=os.environ.get("ECHO_SERVER", "http://127.0.0.1:8080"))
    parser.add_argument("--token", default=os.environ.get("ECHO_TOKEN", ""))
    args = parser.parse_args()

    token = args.token.strip()
    if not token:
        config_path = os.path.join(BASE_DIR, "echo_config.json")
        if os.path.exists(config_path):
            try:
                with open(config_path, "r", encoding="utf-8") as fh:
                    token = str(json.load(fh).get("echo_token", "")).strip()
            except Exception:
                token = ""
    if not token:
        print("[!] Токен не найден: укажите --token или ECHO_TOKEN")
        return 2

    base = args.base
    print(f"ЭХО smoke test → {base}\n")

    print("Публичные страницы и здоровье:")
    status, headers, raw = request(base, "/api/health")
    health = as_json(raw)
    check("/api/health отвечает 200", status == 200, f"HTTP {status}")
    check("в health есть версия и аптайм", "version" in health and "uptimeSec" in health)

    status, headers, raw = request(base, "/")
    check("пульт / отдаёт HTML", status == 200 and "text/html" in headers.get("Content-Type", ""))
    check("cookie токена HttpOnly", "HttpOnly" in headers.get("Set-Cookie", ""), headers.get("Set-Cookie", ""))
    check("CSP включён", "Content-Security-Policy" in headers)
    check("X-Frame-Options: DENY", headers.get("X-Frame-Options") == "DENY")
    check("X-Content-Type-Options: nosniff", headers.get("X-Content-Type-Options") == "nosniff")
    check("новая версия пульта (живой эфир)", "Эфир прямо сейчас".encode("utf-8") in raw)

    status, _headers, raw = request(base, "/app")
    check("сонар /app отдаёт HTML", status == 200 and b"sonarCanvas" in raw)
    check("PWA содержит настройки", "НАСТРОЙКИ".encode("utf-8") in raw)
    check("PWA содержит слой Лагранжа", b"lagrangePoint" in raw)

    print("\nСостояние и плейлист:")
    status, headers, raw = request(base, "/api/state?deviceId=smoke_local")
    state = as_json(raw)
    check("локальный /api/state 200", status == 200, f"HTTP {status}")
    check("есть сигналы и зоны", bool(state.get("signals")) and bool(state.get("cityZones")))
    check("локально токен отдаётся пульту", bool(state.get("serverMeta", {}).get("token")))
    check("в эфире нет служебных треков",
          all("://" not in str(s.get("title", "")) for s in state.get("signals", [])))
    check("только http(s) ссылки обложек допустимы",
          all(str(s.get("coverUrl", "")).startswith("/api/cover/") for s in state.get("signals", []) if s.get("coverUrl")))

    status, _headers, raw = request(base, "/api/playlist?limit=5")
    playlist = as_json(raw)
    check("/api/playlist 200 и текст", status == 200 and bool(playlist.get("text")))
    check("треки плейлиста уникальны",
          len({(t["artist"], t["title"]) for t in playlist.get("tracks", [])}) == len(playlist.get("tracks", [])))

    print("\nАвторизация публичного туннеля:")
    status, _h, raw = request(base, "/api/state", headers=PROXY_HEADERS)
    check("туннель без токена → 401", status == 401, f"HTTP {status}")
    status, _h, _r = request(base, "/api/state", headers={**PROXY_HEADERS, "X-Echo-Token": "wrong-token"})
    check("туннель с неверным токеном → 401", status == 401, f"HTTP {status}")
    status, _h, raw = request(base, "/api/state", headers={**PROXY_HEADERS, "X-Echo-Token": token})
    tunnel_state = as_json(raw)
    check("туннель с верным токеном → 200", status == 200, f"HTTP {status}")
    check("через туннель токен не раскрывается", "token" not in tunnel_state.get("serverMeta", {}))

    status, _h, _r = request(base, "/api/delete_signal", method="POST",
                             payload={"targetId": "smoke_unknown"}, headers=PROXY_HEADERS)
    check("туннель не даёт удалять сигналы без токена", status == 401, f"HTTP {status}")

    print("\nВалидация и фильтры:")
    status, _h, raw = request(base, "/api/signal", method="POST",
                              payload={"id": "smoke_junk_device", **JUNK_TRACK},
                              headers={**PROXY_HEADERS, "X-Echo-Token": token})
    reply = as_json(raw)
    check("служебный трек отклонён", status == 200 and reply.get("status") == "ignored", str(reply))

    status, _h, raw = request(base, "/api/signal", method="POST",
                              payload={"id": "smoke_valid_device", "artist": "Kino", "title": "Gruppa Krovi",
                                       "zoneName": "Городской сектор"},
                              headers={**PROXY_HEADERS, "X-Echo-Token": token})
    reply = as_json(raw)
    check("валидный сигнал принят", status == 200 and reply.get("status") == "ok", str(reply.get("status")))
    _status, _h, state_raw = request(base, "/api/state?deviceId=smoke_observer")
    zone = next((s.get("zoneName") for s in as_json(state_raw).get("signals", [])
                 if s.get("id") == "smoke_valid_device"), None)
    check("неизвестная зона нормализована", zone == "Вечерняя улица", str(zone))

    request(base, "/api/delete_signal", method="POST", payload={"targetId": "smoke_valid_device"},
            headers={**PROXY_HEADERS, "X-Echo-Token": token})

    status, _h, _r = request(base, "/api/unknown-endpoint")
    check("неизвестный путь → 404", status == 404, f"HTTP {status}")

    print(f"\nИтог: {PASSED} успешно, {FAILED} провалено")
    return 0 if FAILED == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
