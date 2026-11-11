#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ЭХО — публикация исходников в GitHub и скачивание собранного APK (deploy_build.py).

Токен GitHub НЕ хранится в коде. Способы задать его:
  1. переменная окружения GITHUB_TOKEN (или GH_TOKEN);
  2. файл deploy_token.txt рядом со скриптом (добавлен в .gitignore).

Примеры:
    python deploy_build.py --dry-run
    python deploy_build.py --message "feat: новый сонар"
    python deploy_build.py --repo owner/repo --branch main

Сценарий: коммит выбранных файлов через GitHub Git Data API → ожидание
GitHub Actions → скачивание артефакта с APK в папку проекта.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_REPO = "kvazibulka-commits/echo-radar-by-Antigravity"
DEFAULT_BRANCH = "main"
DEFAULT_MESSAGE = "chore: синхронизация исходников ЭХО"

# Что публикуем: только текст/исходники, без баз, ключей, APK и кэшей.
FILES_TO_COMMIT = [
    "echo_server.py",
    "echo_pc.py",
    "echo_tunnel.py",
    "echo_archetypes.json",
    "echo_config.example.json",
    "requirements.txt",
    "README.md",
    "CHANGELOG.md",
    ".gitignore",
    "deploy_build.py",
    "RUN-ALL.bat",
    "RUN-SERVER.bat",
    "RUN-PC-MUSIC.bat",
    "templates/dashboard.html",
    "templates/pwa.html",
    "tests/test_echo_server.py",
    "tools/smoke_test.py",
    "tools/cdp_shot.py",
    "tools/publish_release.py",
    "docs/screenshots/dashboard.png",
    "docs/screenshots/pwa-sonar.png",
    "docs/screenshots/pwa-air.png",
    "docs/screenshots/pwa-passport.png",
    "docs/screenshots/pwa-playlist.png",
    "docs/screenshots/pwa-caches.png",
    "docs/screenshots/pwa-settings.png",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/ru/echo/radar/MainActivity.kt",
    "app/src/main/java/ru/echo/radar/EchoStateRepository.kt",
    "app/src/main/java/ru/echo/radar/EchoChronicleManager.kt",
    "app/src/main/java/ru/echo/radar/EchoSoundManager.kt",
    "app/src/main/java/ru/echo/radar/EchoAudioPlayer.kt",
    "app/src/main/java/ru/echo/radar/EchoBleManager.kt",
    "app/src/main/java/ru/echo/radar/AlbumArtResolver.kt",
    "app/src/main/java/ru/echo/radar/EchoMediaListenerService.kt",
    ".github/workflows/build-apk.yml",
]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def load_token() -> str:
    for env_name in ("GITHUB_TOKEN", "GH_TOKEN"):
        value = os.environ.get(env_name, "").strip()
        if value:
            return value
    token_file = os.path.join(BASE_DIR, "deploy_token.txt")
    if os.path.exists(token_file):
        try:
            with open(token_file, "r", encoding="utf-8") as f:
                value = f.read().strip()
            if value:
                return value
        except OSError:
            pass
    print("[!] Не найден токен GitHub.")
    print("    Задайте переменную окружения GITHUB_TOKEN (классический токен с правом repo)")
    print("    или создайте файл deploy_token.txt рядом со скриптом.")
    print("    Важно: токен, который раньше был в этом файле, теперь скомпрометирован —")
    print("    отзовите его на https://github.com/settings/tokens и выпустите новый.")
    sys.exit(2)


def api_request(token: str, repo: str, endpoint: str, method: str = "GET", data: dict | None = None):
    url = endpoint if endpoint.startswith("https://") else f"https://api.github.com/repos/{repo}{endpoint}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "EchoDeploy/2.0",
    }
    body = json.dumps(data).encode("utf-8") if data is not None else None
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    last_error = None
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="ignore")
            if exc.code >= 500 and attempt < 3:
                print(f"[~] GitHub ответил {exc.code}, повтор {attempt}/3 через {2 * attempt} с")
                time.sleep(2 * attempt)
                continue
            if exc.code == 401:
                print("[!] GitHub отклонил токен (401). Проверьте, что он не отозван и имеет право repo.")
            elif exc.code == 403:
                print("[!] GitHub ограничил доступ (403): проверьте права токена и лимиты API.")
            elif exc.code == 404:
                print(f"[!] Репозиторий или ветка не найдены (404): {repo}")
            else:
                print(f"[!] GitHub API вернул {exc.code}: {detail[:300]}")
            raise SystemExit(1)
        except (TimeoutError, urllib.error.URLError, ConnectionError, OSError) as exc:
            last_error = exc
            if attempt < 3:
                print(f"[~] Сетевая ошибка ({exc}), повтор {attempt}/3 через {2 * attempt} с")
                time.sleep(2 * attempt)
                continue
            print(f"[!] Не удалось связаться с GitHub: {last_error}")
            print("    Проверьте интернет/прокси и повторите запуск — коммит не создан.")
            raise SystemExit(1)


def collect_files() -> list[tuple[str, str]]:
    """Возвращает пары (локальный путь, путь в репозитории) для существующих файлов."""
    result = []
    for rel in FILES_TO_COMMIT:
        local = os.path.join(BASE_DIR, rel.replace("/", os.sep))
        if os.path.exists(local):
            result.append((local, rel))
        else:
            print(f"[~] пропуск (нет файла): {rel}")
    return result


def push_commit(token: str, repo: str, branch: str, message: str, dry_run: bool) -> str | None:
    files = collect_files()
    if not files:
        print("[!] Нечего публиковать.")
        return None

    print(f"[1/6] Ветка {repo}@{branch}, файлов к публикации: {len(files)}")
    if dry_run:
        for _, repo_path in files:
            print(f"      + {repo_path}")
        print("[dry-run] Изменения не отправлены.")
        return None

    ref = api_request(token, repo, f"/git/ref/heads/{branch}")
    latest_sha = ref["object"]["sha"]
    base_tree = api_request(token, repo, f"/git/commits/{latest_sha}")["tree"]["sha"]
    print(f"      база: {latest_sha[:10]}")

    tree_elements = []
    for local_path, repo_path in files:
        with open(local_path, "rb") as fh:
            raw = fh.read()
        try:
            # Текстовые файлы отправляем как utf-8, картинки и прочие бинарники — base64
            blob_payload = {"content": raw.decode("utf-8"), "encoding": "utf-8"}
        except UnicodeDecodeError:
            blob_payload = {"content": base64.b64encode(raw).decode("ascii"), "encoding": "base64"}
        blob = api_request(token, repo, "/git/blobs", method="POST", data=blob_payload)
        tree_elements.append({"path": repo_path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print(f"      blob {repo_path} ({len(raw)} байт)")

    tree = api_request(token, repo, "/git/trees", method="POST",
                       data={"base_tree": base_tree, "tree": tree_elements})
    commit = api_request(token, repo, "/git/commits", method="POST",
                         data={"message": message, "tree": tree["sha"], "parents": [latest_sha]})
    api_request(token, repo, f"/git/refs/heads/{branch}", method="PATCH",
                data={"sha": commit["sha"], "force": False})
    print(f"[2/6] Коммит отправлен: {commit['sha'][:10]}")
    return commit["sha"]


def wait_for_run(token: str, repo: str, commit_sha: str) -> int | None:
    print("[3/6] Ждём запуск GitHub Actions…")
    run_id = None
    for _ in range(40):
        time.sleep(3)
        runs = api_request(token, repo, "/actions/runs?per_page=10")
        for run in runs.get("workflow_runs", []):
            if run.get("head_sha") == commit_sha:
                run_id = run["id"]
                print(f"      workflow запущен: id={run_id}")
                break
        if run_id:
            break
    if not run_id:
        print("[!] Не нашли workflow для этого коммита. Проверьте вкладку Actions.")
        return None

    while True:
        time.sleep(10)
        run = api_request(token, repo, f"/actions/runs/{run_id}")
        status, conclusion = run.get("status"), run.get("conclusion")
        print(f"      сборка: {status} / {conclusion or '…'}")
        if status == "completed":
            if conclusion != "success":
                print(f"[!] Сборка завершилась с результатом {conclusion}. Смотрите лог Actions.")
                return None
            print("[4/6] Сборка успешна.")
            return run_id


def download_artifact(token: str, repo: str, run_id: int) -> str | None:
    print("[5/6] Ищем артефакт APK…")
    artifacts = api_request(token, repo, f"/actions/runs/{run_id}/artifacts")
    items = artifacts.get("artifacts", [])
    if not items:
        print("[!] Артефактов нет.")
        return None
    artifact = items[0]
    print(f"      артефакт: {artifact['name']} ({artifact['size_in_bytes']} байт)")

    zip_path = os.path.join(BASE_DIR, "artifact_download.zip")
    opener = urllib.request.build_opener(NoRedirect)
    request = urllib.request.Request(
        artifact["archive_download_url"],
        headers={"Authorization": f"Bearer {token}", "User-Agent": "EchoDeploy/2.0"},
    )
    try:
        opener.open(request, timeout=45)
        print("[!] Ожидался редирект на CDN, но его не было.")
        return None
    except urllib.error.HTTPError as exc:
        if exc.code not in (301, 302, 307):
            print(f"[!] Не удалось скачать артефакт: HTTP {exc.code}")
            return None
        location = exc.headers.get("Location")
        if not location:
            print("[!] Ответ без Location.")
            return None
        urllib.request.urlretrieve(location, zip_path)

    print("[6/6] Распаковываем APK…")
    extract_dir = os.path.join(BASE_DIR, "extracted_apk")
    os.makedirs(extract_dir, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(extract_dir)

    apk_path = None
    for root, _dirs, names in os.walk(extract_dir):
        for name in names:
            if name.endswith(".apk"):
                apk_path = os.path.join(root, name)
                break
        if apk_path:
            break

    if not apk_path:
        print("[!] В архиве нет APK.")
        return None

    destination = os.path.join(BASE_DIR, "app-debug.apk")
    with open(apk_path, "rb") as src, open(destination, "wb") as dst:
        dst.write(src.read())
    os.remove(zip_path)
    print(f"[Готово] APK обновлён: {destination} ({os.path.getsize(destination)} байт)")
    return destination


def main() -> int:
    parser = argparse.ArgumentParser(description="Публикация исходников ЭХО и сборка APK через GitHub Actions")
    parser.add_argument("--repo", default=os.environ.get("ECHO_REPO", DEFAULT_REPO))
    parser.add_argument("--branch", default=os.environ.get("ECHO_BRANCH", DEFAULT_BRANCH))
    parser.add_argument("--message", default=os.environ.get("ECHO_COMMIT_MESSAGE", DEFAULT_MESSAGE))
    parser.add_argument("--dry-run", action="store_true", help="только показать список файлов")
    args = parser.parse_args()

    if args.dry_run:
        files = collect_files()
        print(f"[dry-run] Файлов к публикации: {len(files)} (токен не требуется)")
        for _, repo_path in files:
            print(f"      + {repo_path}")
        return 0

    token = load_token()
    commit_sha = push_commit(token, args.repo, args.branch, args.message, dry_run=False)
    if not commit_sha:
        return 1

    run_id = wait_for_run(token, args.repo, commit_sha)
    if not run_id:
        return 1
    return 0 if download_artifact(token, args.repo, run_id) else 1


if __name__ == "__main__":
    raise SystemExit(main())
