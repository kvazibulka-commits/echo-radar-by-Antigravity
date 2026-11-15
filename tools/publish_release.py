#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ЭХО — публикация GitHub Release с APK (tools/publish_release.py).

Токен берётся из GITHUB_TOKEN / GH_TOKEN или из deploy_token.txt (gitignored).
Релиз создаётся один раз на тег; повторный запуск с тем же тегом ничего не ломает.

Примеры:
    python tools/publish_release.py --tag v3.1.0
    python tools/publish_release.py --tag v3.2.0 --name "ЭХО 3.2" --body-file release_notes.md
    python tools/publish_release.py --tag v3.2.0 --apk build/app-debug.apk
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_REPO = "kvazibulka-commits/echo-radar-by-Antigravity"
DEFAULT_BODY = """## ЭХО — новая сборка

- Android debug APK во вложении.
- Сервер и веб-интерфейсы — в исходниках репозитория.
- Перед подключением укажите в приложении ⚙ Сервер адрес и токен из консоли `echo_server.py`.

> APK подписан debug-ключом и предназначен для тестирования.
"""


def load_token() -> str:
    for env_name in ("GITHUB_TOKEN", "GH_TOKEN"):
        value = os.environ.get(env_name, "").strip()
        if value:
            return value
    token_file = os.path.join(BASE_DIR, "deploy_token.txt")
    if os.path.exists(token_file):
        with open(token_file, "r", encoding="utf-8") as fh:
            value = fh.read().strip()
        if value:
            return value
    print("[!] Нет токена: задайте GITHUB_TOKEN или создайте deploy_token.txt")
    raise SystemExit(2)


def call(token: str, url: str, method: str = "GET", data=None,
         content_type: str = "application/json", timeout: int = 180):
    body = None
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "EchoRelease/1.0",
    }
    if data is not None:
        body = data if isinstance(data, bytes) else json.dumps(data).encode("utf-8")
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read()
        return response.status, (json.loads(raw.decode("utf-8")) if raw else {})


def main() -> int:
    parser = argparse.ArgumentParser(description="Создать GitHub Release и приложить APK")
    parser.add_argument("--repo", default=os.environ.get("ECHO_REPO", DEFAULT_REPO))
    parser.add_argument("--tag", required=True)
    parser.add_argument("--name", default="")
    parser.add_argument("--body-file", default="")
    parser.add_argument("--apk", default=os.path.join(BASE_DIR, "app-debug.apk"))
    parser.add_argument("--target", default="main")
    args = parser.parse_args()

    token = load_token()
    if not os.path.exists(args.apk):
        print(f"[!] APK не найден: {args.apk}")
        return 1
    apk_size = os.path.getsize(args.apk)
    if apk_size < 1_000_000:
        print(f"[!] Подозрительно маленький APK ({apk_size} байт) — сборка неполная?")
        return 1

    try:
        status, existing = call(token, f"https://api.github.com/repos/{args.repo}/releases/tags/{args.tag}")
        if status == 200:
            print(f"Релиз {args.tag} уже существует: {existing.get('html_url')}")
            return 0
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            print(f"[!] Проверка релиза: HTTP {exc.code}")
            return 1

    body = DEFAULT_BODY
    if args.body_file:
        with open(args.body_file, "r", encoding="utf-8") as fh:
            body = fh.read()
    name = args.name.strip() or f"ЭХО {args.tag}"

    print(f"Создаю релиз {args.tag} в {args.repo}…")
    _, release = call(
        token,
        f"https://api.github.com/repos/{args.repo}/releases",
        method="POST",
        data={
            "tag_name": args.tag,
            "target_commitish": args.target,
            "name": name,
            "body": body,
            "draft": False,
            "prerelease": False,
        },
    )
    print("Релиз:", release.get("html_url"))

    upload_url = release["upload_url"].split("{")[0]
    asset_name = f"echo-radar-{args.tag.lstrip('v')}-debug.apk"
    with open(args.apk, "rb") as fh:
        apk_bytes = fh.read()
    print(f"Загружаю {asset_name}: {len(apk_bytes)} байт…")
    _, asset = call(
        token,
        f"{upload_url}?name={asset_name}",
        method="POST",
        data=apk_bytes,
        content_type="application/vnd.android.package-archive",
    )
    print("Скачать APK:", asset.get("browser_download_url"))
    print("Размер на GitHub:", asset.get("size"), "байт")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
