#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ЭХО — установка проектных скилов в каталог агента (tools/install_skills.py).

Копирует папки из `skills/` в `%USERPROFILE%\\.agents\\skills\\`, не трогая
остальные скилы (autoglm и другие).

Примеры:
    python tools/install_skills.py                 # установить новые скилы
    python tools/install_skills.py --force         # обновить существующие
    python tools/install_skills.py --dry-run       # показать план
    python tools/install_skills.py --list          # что уже установлено
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_DIR = os.path.join(BASE_DIR, "skills")
TARGET_DIR = os.path.join(os.path.expanduser("~"), ".agents", "skills")


def discover():
    """Возвращает [(имя, путь)] для папок со SKILL.md."""
    if not os.path.isdir(SOURCE_DIR):
        print(f"[!] Нет папки скилов: {SOURCE_DIR}")
        return []
    result = []
    for name in sorted(os.listdir(SOURCE_DIR)):
        path = os.path.join(SOURCE_DIR, name)
        if os.path.isdir(path) and os.path.exists(os.path.join(path, "SKILL.md")):
            result.append((name, path))
    return result


def frontmatter_name(skill_path: str) -> str:
    """Читает name: из YAML-заголовка, не требуя PyYAML."""
    try:
        with open(os.path.join(skill_path, "SKILL.md"), "r", encoding="utf-8") as fh:
            lines = fh.read(4000).splitlines()
    except OSError:
        return ""
    if not lines or lines[0].strip() != "---":
        return ""
    for line in lines[1:]:
        if line.strip() == "---":
            break
        if line.startswith("name:"):
            return line.split(":", 1)[1].strip().strip("\"'")
    return ""


def list_installed() -> int:
    if not os.path.isdir(TARGET_DIR):
        print(f"Каталог скилов не найден: {TARGET_DIR}")
        return 0
    names = sorted(
        name for name in os.listdir(TARGET_DIR)
        if os.path.exists(os.path.join(TARGET_DIR, name, "SKILL.md"))
    )
    print(f"Установлено ({len(names)}) в {TARGET_DIR}:")
    for name in names:
        marker = "echo" if name.startswith("echo-") else "—"
        print(f"  [{marker:>4}] {name}")
    return 0


def install(force: bool, dry_run: bool) -> int:
    skills = discover()
    if not skills:
        print("[!] Скилы не найдены. Ожидаются папки вида skills/<name>/SKILL.md")
        return 1

    print(f"Источник: {SOURCE_DIR}")
    print(f"Цель:     {TARGET_DIR}")
    if not dry_run:
        os.makedirs(TARGET_DIR, exist_ok=True)

    installed, skipped, updated, failed = 0, 0, 0, 0
    for name, src in skills:
        declared = frontmatter_name(src)
        if declared and declared != name:
            print(f"  [!] {name}: в frontmatter указано name: {declared} — должно совпадать с папкой")
            failed += 1
            continue

        dest = os.path.join(TARGET_DIR, name)
        exists = os.path.exists(dest)
        if exists and not force:
            print(f"  [=] {name}: уже установлен (--force для обновления)")
            skipped += 1
            continue
        if dry_run:
            action = "обновить" if exists else "установить"
            print(f"  [~] {name}: {action} (dry-run)")
            continue

        try:
            if exists:
                shutil.rmtree(dest)
            shutil.copytree(
                src,
                dest,
                ignore=shutil.ignore_patterns("__pycache__", "*.pyc", ".DS_Store"),
            )
            print(f"  [+] {name}: {'обновлён' if exists else 'установлен'}")
            updated += 1 if exists else 0
            installed += 0 if exists else 1
        except OSError as exc:
            print(f"  [!] {name}: ошибка копирования — {exc}")
            failed += 1

    print(
        f"\nИтог: установлено {installed}, обновлено {updated}, "
        f"пропущено {skipped}, ошибок {failed}"
    )
    if not dry_run and (installed or updated):
        print("Скилы появятся в каталоге агента в новой сессии (некоторые сборки — сразу).")
        print("Проверка: попросить агента загрузить, например, echo-visual-design.")
    return 0 if failed == 0 else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Установка скилов ЭХО в каталог агента")
    parser.add_argument("--force", action="store_true", help="перезаписать существующие скилы")
    parser.add_argument("--dry-run", action="store_true", help="только показать план")
    parser.add_argument("--list", action="store_true", help="показать установленные скилы")
    args = parser.parse_args()

    if args.list:
        return list_installed()
    return install(force=args.force, dry_run=args.dry_run)


if __name__ == "__main__":
    raise SystemExit(main())
