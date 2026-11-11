#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Юнит-тесты ядра ЭХО (stdlib unittest, без внешних зависимостей).

Запуск:
    python -m unittest discover -s tests -v

Тесты работают с временной базой и временным конфигом, реальные
echo_memory.db / echo_config.json не затрагиваются.
"""

from __future__ import annotations

import json
import os
import shutil
import sqlite3
import sys
import tempfile
import time
import unittest

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

# ВАЖНО: окружение настраивается до импорта сервера — пути читаются на уровне модуля.
TMP_DIR = tempfile.mkdtemp(prefix="echo_tests_")
TEST_TOKEN = "test-token-0123456789abcdef"
os.environ["ECHO_DB_PATH"] = os.path.join(TMP_DIR, "test_echo.db")
os.environ["ECHO_CONFIG_PATH"] = os.path.join(TMP_DIR, "test_config.json")
os.environ["ECHO_TOKEN"] = TEST_TOKEN
os.environ["ECHO_ARCHETYPES_PATH"] = os.path.join(BASE_DIR, "echo_archetypes.json")
with open(os.environ["ECHO_CONFIG_PATH"], "w", encoding="utf-8") as _cfg:
    json.dump({"gemini_api_key": "", "echo_token": TEST_TOKEN}, _cfg, ensure_ascii=False)

import echo_server  # noqa: E402


def tearDownModule():
    shutil.rmtree(TMP_DIR, ignore_errors=True)


class TestTrackFilters(unittest.TestCase):
    def test_junk_tracks_rejected(self):
        cases = [
            ("", "Systrum Sistum | SSR (308046B0AF4A39CB)"),
            ("", ""),
            ("", "chrome-extension://abc/offscreen.html"),
            ("Browser", "file:///C:/music.mp3"),
            ("", "about:blank"),
            ("Artist", "x" * 240),
        ]
        for artist, title in cases:
            with self.subTest(artist=artist, title=title):
                self.assertTrue(echo_server.is_junk_track(artist, title))

    def test_real_tracks_pass(self):
        cases = [
            ("Sirotkin", "Август"),
            ("Молчат Дома", "Судно"),
            ("Burial", "Archangel"),
            ("", "Подкаст о ночном городе"),
            ("Chet Baker", "I Fall In Love Too Easily"),
        ]
        for artist, title in cases:
            with self.subTest(artist=artist, title=title):
                self.assertFalse(echo_server.is_junk_track(artist, title))


class TestTextHelpers(unittest.TestCase):
    def test_sanitize_text_strips_control_and_limits(self):
        self.assertEqual(echo_server.sanitize_text("  a\x00b\nc  "), "a b c")
        self.assertEqual(len(echo_server.sanitize_text("ж" * 500)), echo_server.MAX_TEXT_LEN)
        self.assertEqual(echo_server.sanitize_text(None), "")

    def test_normalize_zone(self):
        self.assertEqual(echo_server.normalize_zone("Городской сектор"), "Вечерняя улица")
        self.assertEqual(echo_server.normalize_zone(""), "Вечерняя улица")
        self.assertEqual(echo_server.normalize_zone("Сретенка"), "Сретенка · Камерные бары")
        self.assertEqual(echo_server.normalize_zone("Парк Горького · Набережная"), "Парк Горького · Набережная")
        self.assertEqual(echo_server.normalize_zone("Северный лесопарк"), "Северный лесопарк")


class TestResonanceMath(unittest.TestCase):
    def test_tempo_harmonic(self):
        self.assertAlmostEqual(echo_server.calculate_tempo_harmonic(120, 120), 1.0, places=2)
        self.assertGreater(echo_server.calculate_tempo_harmonic(120, 60), 0.9)  # октава
        self.assertLess(echo_server.calculate_tempo_harmonic(120, 155), 0.3)
        self.assertEqual(echo_server.calculate_tempo_harmonic(0, 120), 0.75)

    def test_camelot(self):
        self.assertEqual(echo_server.calculate_camelot_harmonic("8A", "8A"), 1.0)
        self.assertEqual(echo_server.calculate_camelot_harmonic("8A", "8B"), 0.95)
        self.assertEqual(echo_server.calculate_camelot_harmonic("8A", "9A"), 0.90)
        self.assertGreaterEqual(echo_server.calculate_camelot_harmonic("1A", "12A"), 0.90)  # круг замкнут

    def test_mood_vector(self):
        self.assertAlmostEqual(echo_server.calculate_mood_vector(0.5, 0.5, 0.5, 0.5), 1.0, places=2)
        self.assertLess(echo_server.calculate_mood_vector(0.0, 0.0, 1.0, 1.0), 0.2)

    def test_serendipity_bridge(self):
        score, desc = echo_server.calculate_serendipity_engine("post-punk", "shoegaze", 0.8)
        self.assertGreater(score, 0)
        self.assertTrue(desc)
        self.assertEqual(echo_server.calculate_serendipity_engine("indie", "indie", 0.8), (0.0, ""))

    def test_full_resonance_bounds(self):
        track = {"camelotKey": "8A", "bpm": 120, "valence": 0.5, "energy": 0.6, "genre": "indie"}
        other = {"camelotKey": "8B", "bpm": 60, "valence": 0.5, "energy": 0.6, "genre": "post-punk"}
        compat, story = echo_server.calculate_full_resonance(track, other)
        self.assertIsInstance(compat, int)
        self.assertGreaterEqual(compat, 58)
        self.assertLessEqual(compat, 99)
        self.assertTrue(story)


class TestDatabaseAndState(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        echo_server.init_db()

    def test_config_uses_env_token(self):
        self.assertEqual(echo_server.SERVER_CONFIG.get("echo_token"), TEST_TOKEN)

    def test_no_hardcoded_token_in_source(self):
        with open(os.path.join(BASE_DIR, "echo_server.py"), "r", encoding="utf-8") as fh:
            source = fh.read()
        self.assertNotIn("1c13ee8697755fdc4518013ea2fd6996", source)

    def test_state_payload_has_seeds_and_meta(self):
        payload = echo_server.build_state_payload()
        self.assertEqual(payload["status"], "ok")
        self.assertGreaterEqual(len(payload["signals"]), 3)
        self.assertIn("serverMeta", payload)
        self.assertGreaterEqual(len(payload["cityZones"]), 1)
        for signal in payload["signals"]:
            self.assertFalse(echo_server.is_junk_track(signal["artist"], signal["title"]))
            self.assertGreaterEqual(signal["compatibilityPercent"], 58)
            self.assertLessEqual(signal["compatibilityPercent"], 99)

    def test_token_only_shown_on_request(self):
        local = echo_server.build_state_payload(include_token=True)
        remote = echo_server.build_state_payload(include_token=False)
        self.assertEqual(local["serverMeta"].get("token"), TEST_TOKEN)
        self.assertNotIn("token", remote["serverMeta"])

    def test_signal_written_and_read_back(self):
        now = time.time()
        conn = echo_server.get_db_connection()
        cur = conn.cursor()
        cur.execute(
            "INSERT OR REPLACE INTO devices (id, artist, title, zone_name, updated_at) VALUES (?, ?, ?, ?, ?)",
            ("test_device_unit", "Kino", "Gruppa Krovi", "Вечерняя улица", now),
        )
        conn.commit()
        conn.close()

        public_payload = echo_server.build_state_payload()
        self.assertIn("test_device_unit", [s["id"] for s in public_payload["signals"]])

        own_payload = echo_server.build_state_payload(for_device_id="test_device_unit")
        self.assertEqual(own_payload["myDeviceId"], "test_device_unit")
        # собственный маяк не показывается самому себе на сонаре
        self.assertNotIn("test_device_unit", [s["id"] for s in own_payload["signals"]])

    def test_reaction_history_visibility(self):
        with echo_server.REACTIONS_LOCK:
            echo_server.RECENT_REACTIONS.append({
                "id": echo_server.next_event_id(), "targetId": "sig_sirotkin_01",
                "senderId": "someone_else", "symbol": "✦", "message": "публичный отклик",
                "time": time.time(),
            })
            echo_server.RECENT_REACTIONS.append({
                "id": echo_server.next_event_id(), "targetId": "private_device_xyz",
                "senderId": "someone_else", "symbol": "🌙", "message": "личный отклик",
                "time": time.time(),
            })

        observer = echo_server.build_state_payload(for_device_id="observer_device")
        messages = [r["message"] for r in observer["reactionsHistory"]]
        self.assertIn("публичный отклик", messages)
        self.assertNotIn("личный отклик", messages)

        dashboard = echo_server.build_state_payload(for_device_id="dashboard")
        dashboard_messages = [r["message"] for r in dashboard["reactionsHistory"]]
        self.assertIn("личный отклик", dashboard_messages)

    def test_playlist_unique_and_linked(self):
        conn = echo_server.get_db_connection()
        cur = conn.cursor()
        for _ in range(3):
            cur.execute(
                "INSERT INTO listening_history (device_id, artist, title, persona_title, zone_name, played_at, timestamp) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                ("test_device_unit", "Kino", "Gruppa Krovi", "Пилигрим", "Вечерняя улица", "23:10", time.time()),
            )
        cur.execute(
            "INSERT INTO listening_history (device_id, artist, title, persona_title, zone_name, played_at, timestamp) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ("test_device_unit", "Sirotkin", "Август", "Архивариус", "Вечерняя улица", "23:14", time.time()),
        )
        conn.commit()
        conn.close()

        data = echo_server.build_evening_playlist(limit=10)
        self.assertEqual(data["status"], "ok")
        keys = [(t["artist"], t["title"]) for t in data["tracks"]]
        self.assertEqual(len(keys), len(set(keys)), "в плейлисте не должно быть дублей")
        self.assertIn(("Kino", "Gruppa Krovi"), keys)
        self.assertTrue(data["text"].startswith("✦ Плейлист"))
        for track in data["tracks"]:
            self.assertIn("music.yandex.ru", track["yandexUrl"])
            self.assertIn("music.apple.com", track["appleUrl"])


class TestCaches(unittest.TestCase):
    def test_lru_cache_evicts(self):
        cache = echo_server.LRUCache(maxsize=2)
        cache["a"] = 1
        cache["b"] = 2
        _ = cache["a"]
        cache["c"] = 3
        self.assertIn("a", cache)
        self.assertNotIn("b", cache)
        self.assertIn("c", cache)

    def test_rate_limiter(self):
        ip = "203.0.113.77"
        echo_server.RATE_BUCKETS.pop(ip, None)
        allowed = sum(1 for _ in range(5) if echo_server.rate_limit_allow(ip, limit=3))
        self.assertEqual(allowed, 3)


class TestArchetypes(unittest.TestCase):
    def test_library_matches_known_artist(self):
        matched = echo_server.find_archetype_in_library("Молчат Дома", "", "")
        self.assertIsNotNone(matched)
        self.assertIn("personaTitle", matched)
        self.assertTrue(matched["personaTitle"])

    def test_genre_cluster_fallback(self):
        matched = echo_server.find_archetype_in_library("Неизвестный", "", "post-punk")
        self.assertIsNotNone(matched)
        self.assertIn("moodLabel", matched)


if __name__ == "__main__":
    unittest.main(verbosity=2)
