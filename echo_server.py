#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ЭХО (Echo Radar) — Сервер музыкального присутствия (echo_server.py)
Философия: «Ты видишь не людей вокруг. Ты видишь музыку, которая сейчас живет вокруг тебя».

Ключевые возможности:
- Оптимизированный SQLite в режиме WAL + busy_timeout (без блокировок при параллельных запросах)
- Двусторонняя синхронизация myColorHex, myReactions, incomingBanner и senderId
- Музыкальные тайники локаций (B2B без рекламы) и Звуковой туман кварталов (Ambient Audio Fog)
- «Призрачные следы» (Ghost Echoes) с авто-очисткой через 90 минут
- Сенсорный сонар и карта на /app с кнопкой сборки плейлиста вечера
- Автоматический UDP-маяк автопоиска (порт 8888)
"""

import concurrent.futures
import hashlib
import json
import logging
import math
import os
import random
import re
import secrets
import socket
import sqlite3
import threading
import time
import urllib.parse
import urllib.request
from collections import OrderedDict
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("echo")


class LRUCache:
    def __init__(self, maxsize=500):
        self._cache = OrderedDict()
        self._maxsize = maxsize
    def __contains__(self, key):
        return key in self._cache
    def __getitem__(self, key):
        self._cache.move_to_end(key)
        return self._cache[key]
    def __setitem__(self, key, value):
        if key in self._cache:
            self._cache.move_to_end(key)
        self._cache[key] = value
        if len(self._cache) > self._maxsize:
            self._cache.popitem(last=False)

HTTP_PORT = int(os.environ.get("PORT", 8080))
UDP_DISCOVERY_PORT = 8888
DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "echo_memory.db")
CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "echo_config.json")
ARCHETYPES_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "echo_archetypes.json")

def load_archetypes_library():
    if os.path.exists(ARCHETYPES_PATH):
        try:
            with open(ARCHETYPES_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            logger.warning("Failed to load echo_archetypes.json: %s", e)
    return {"curated_records": {}, "genre_clusters": {}}

ARCHETYPES_LIBRARY = load_archetypes_library()

def save_curated_record_to_library(key: str, data: dict):
    try:
        ARCHETYPES_LIBRARY.setdefault("curated_records", {})[key] = {
            "personaTitle": data["personaTitle"],
            "moodLabel": data["moodLabel"],
            "personaObservation": data["personaObservation"],
            "colorHex": data["colorHex"],
            "avatarPetals": data["avatarPetals"],
            "compatibilityPercent": data["compatibilityPercent"]
        }
        with open(ARCHETYPES_PATH, "w", encoding="utf-8") as f:
            json.dump(ARCHETYPES_LIBRARY, f, ensure_ascii=False, indent=2)
        logger.info("✨ Новый архетип добавлен в библиотеку: %s -> %s", key, data.get("personaTitle"))
    except Exception as e:
        logger.debug("Failed to save archetype to library: %s", e)

def find_archetype_in_library(artist: str, album: str, genre: str):
    curated = ARCHETYPES_LIBRARY.get("curated_records", {})
    genre_clusters = ARCHETYPES_LIBRARY.get("genre_clusters", {})

    art_clean = artist.strip().lower()
    alb_clean = album.strip().lower()
    gen_clean = genre.strip().lower()

    if alb_clean:
        key_album = f"{art_clean}::{alb_clean}"
        if key_album in curated:
            return dict(curated[key_album])
        for k, v in curated.items():
            if "::" in k:
                k_art, k_alb = k.split("::", 1)
                if k_art == art_clean and (k_alb in alb_clean or alb_clean in k_alb):
                    return dict(v)

    if art_clean in curated:
        return dict(curated[art_clean])
    for k, v in curated.items():
        if "::" not in k and (k in art_clean or art_clean in k):
            return dict(v)

    if gen_clean:
        for g_name, g_data in genre_clusters.items():
            if g_name in gen_clean or gen_clean in g_name:
                return dict(g_data)

    return None

DB_LOCK = threading.Lock()
REACTIONS_LOCK = threading.Lock()
METADATA_CACHE = LRUCache(maxsize=500)
AI_PERSONA_CACHE = LRUCache(maxsize=500)
RECENT_REACTIONS = []
_PERSONA_EXECUTOR = None

ZONE_COORDINATES = {
    "Вечерняя улица": (55.7602, 37.6185),            # Камергерский / Кузнецкий Мост
    "Сретенка · Камерные бары": (55.7685, 37.6324),   # Переулки Сретенки
    "Парк Горького · Набережная": (55.7294, 37.6022), # Крымская набережная / парк
    "Покровка · Ночные кофейни": (55.7588, 37.6482),  # Покровка / Хохловка
    "Северный лесопарк": (55.8305, 37.5310),         # Тимирязевский / парковый пояс
}
DEFAULT_COORDS = (55.7558, 37.6176)


def get_db_connection():
    conn = sqlite3.connect(DB_PATH, timeout=10.0)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA synchronous=NORMAL;")
    return conn


def ensure_opaque_color(val: int) -> int:
    rgb = int(val) & 0x00FFFFFF
    return 0xFF38BDF8 if rgb == 0 else (rgb | 0xFF000000)


def get_zone_jittered_coords(zone_name: str, seed_str: str):
    anchor = ZONE_COORDINATES.get(zone_name, DEFAULT_COORDS)
    h = int(hashlib.md5(seed_str.encode("utf-8")).hexdigest()[:8], 16)
    lat_jitter = ((h % 360) - 180) / 100000.0
    lon_jitter = (((h >> 8) % 360) - 180) / 60000.0
    return round(anchor[0] + lat_jitter, 6), round(anchor[1] + lon_jitter, 6)


CITY_ZONES = [
    {
        "placeName": "Вечерняя улица",
        "dominantVibe": "Бархатный синт и теплый инди",
        "recentArtists": "Sirotkin, Pompeya, Кино",
        "defaultColor": 0xFF38BDF8,
        "listenersCount": 5
    },
    {
        "placeName": "Сретенка · Камерные бары",
        "dominantVibe": "Полуночный пост-панк и винил",
        "recentArtists": "Молчат Дома, Ploho, Дурной Вкус",
        "defaultColor": 0xFFA855F7,
        "listenersCount": 6
    },
    {
        "placeName": "Парк Горького · Набережная",
        "dominantVibe": "Дрим-поп и светлая меланхолия",
        "recentArtists": "Моя Мишель, Zoloto, Тося Чайкина",
        "defaultColor": 0xFF34D399,
        "listenersCount": 4
    },
    {
        "placeName": "Покровка · Ночные кофейни",
        "dominantVibe": "Лоу-фай, нео-соул и теплый грув",
        "recentArtists": "Антоха МС, Lovanda, Cream Soda",
        "defaultColor": 0xFFFBBF24,
        "listenersCount": 3
    },
    {
        "placeName": "Северный лесопарк",
        "dominantVibe": "Акустический эмбиент и ночной рок",
        "recentArtists": "Сплин, Би-2, Земфира",
        "defaultColor": 0xFF818CF8,
        "listenersCount": 2
    }
]

ORGANIC_PERSONAS = [
    ("Архивариус забытых кассет", "Теплый винил", 0xFF38BDF8, "Слушает пленочные реверберации, превращая шум проспектов в медленный кадр кино."),
    ("Проводник туманов", "Северный эмбиент", 0xFF818CF8, "Ведет невидимую тропу сквозь сумерки под медленные переливы гитарного дилея."),
    ("Хранитель северных лесов", "Холодная акустика", 0xFF34D399, "В его наушниках шумит ветер среди высоких сосен и потрескивает ночной костер."),
    ("Меланхоличный космонавт", "Аналоговый синт", 0xFFA855F7, "Наблюдает за огнями ночного города как за далекими созвездиями из иллюминатора."),
    ("Ловец виниловых волн", "Бархатный инди", 0xFF38BDF8, "Собирает тепло ламповых записей и легкую ностальгию уходящего вечера."),
    ("Странник сонных набережных", "Вечерний дрим-поп", 0xFFF472B6, "Идет вдоль воды, где каждый гитарный аккорд отражается в темной ряби реки."),
    ("Пилигрим неоновых панелек", "Холодный пост-панк", 0xFF93C5FD, "Вслушивается в упругий бас пустых дворов, храня верность честному звуку."),
    ("Созерцатель тихих чердаков", "Лоу-фай меланхолия", 0xFFFBBF24, "Ловит неспешные клавишные аккорды под мягкий стук редких капель дождя по жести."),
    ("Штурман полуночных мостов", "Глубокий трип-хоп", 0xFF6366F1, "Синхронизирует дыхание с плотным кинематографичным битом ночного мегаполиса."),
    ("Алхимик забытых мелодий", "Акустический нео-соул", 0xFF2DD4BF, "Превращает простую прогулку по переулкам в чистое поэтическое переживание.")
]


def load_config():
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
            # Если токена нет — генерируем и сохраняем
            if not cfg.get("echo_token"):
                cfg["echo_token"] = secrets.token_hex(16)
                with open(CONFIG_PATH, "w", encoding="utf-8") as fw:
                    json.dump(cfg, fw, ensure_ascii=False, indent=2)
            return cfg
        except Exception as e:
            logger.warning("Config load error: %s", e)

    cfg = {
        "gemini_api_key": os.environ.get("GEMINI_API_KEY", ""),
        "echo_token": os.environ.get("ECHO_TOKEN", secrets.token_hex(16))
    }
    return cfg


def save_config(cfg):
    try:
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False, indent=2)
    except Exception as e:
        logger.debug("save_config error: %s", e)


SERVER_CONFIG = load_config()

_PERSONA_EXECUTOR = concurrent.futures.ThreadPoolExecutor(max_workers=3, thread_name_prefix="echo_persona")


def get_local_ip():
    try:
        hostname = socket.gethostname()
        all_ips = socket.gethostbyname_ex(hostname)[2]
        for ip in all_ips:
            if ip.startswith("192.168.") or ip.startswith("172.16.") or ip.startswith("172.20."):
                return ip
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception as e:
        logger.debug("get_local_ip error: %s", e)
        return "127.0.0.1"


def seed_default_devices(cur, now):
    s1_lat, s1_lon = get_zone_jittered_coords("Вечерняя улица", "seed1")
    s2_lat, s2_lon = get_zone_jittered_coords("Сретенка · Камерные бары", "seed2")
    s3_lat, s3_lon = get_zone_jittered_coords("Парк Горького · Набережная", "seed3")

    seed_signals = [
        (
            "sig_sirotkin_01", "Sirotkin", "Август", "Яндекс Музыка",
            "Архивариус забытых кассет",
            "Слушает теплые гитарные реверберации, превращая суету улицы в медленный ночной фильм.",
            "Теплый винил", 0xFF38BDF8, 6, "Вечерняя улица", 38, 35.0, 0.32, 94, 3, 4,
            json.dumps({"✦": 4, "🌙": 2}, ensure_ascii=False), s1_lat, s1_lon, now
        ),
        (
            "sig_molchat_02", "Молчат Дома", "Судно", "VK Музыка",
            "Пилигрим неоновых панелек",
            "Вслушивается в упругий бас пустых переулков, удерживая пульс вечернего города.",
            "Холодный пост-панк", 0xFFA855F7, 8, "Сретенка · Камерные бары", 75, 145.0, 0.52, 88, 2, 3,
            json.dumps({"🔥": 3, "🌊": 1}, ensure_ascii=False), s2_lat, s2_lon, now
        ),
        (
            "sig_pompeya_03", "Pompeya", "90", "Яндекс Музыка",
            "Ловец виниловых волн",
            "Несет в наушниках солнечный дрим-грув и легкую ностальгию уходящего дня.",
            "Бархатный инди", 0xFF34D399, 5, "Парк Горького · Набережная", 115, 255.0, 0.68, 81, 1, 1,
            json.dumps({"✦": 2}, ensure_ascii=False), s3_lat, s3_lon, now
        )
    ]
    cur.executemany("""
        INSERT OR REPLACE INTO devices (
            id, artist, title, source_name, persona_title, persona_observation,
            mood_label, color_hex, avatar_petals, zone_name, distance_meters,
            angle_degrees, distance_ratio, compatibility_percent, resonance_days,
            encounters_count, reactions_json, lat, lon, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, seed_signals)

def init_db():
    with DB_LOCK:
        conn = get_db_connection()
        cur = conn.cursor()

        cur.execute("""
            CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY,
                artist TEXT,
                title TEXT,
                source_name TEXT,
                persona_title TEXT,
                persona_observation TEXT,
                mood_label TEXT,
                color_hex INTEGER,
                avatar_petals INTEGER,
                zone_name TEXT,
                distance_meters INTEGER,
                angle_degrees REAL,
                distance_ratio REAL,
                compatibility_percent INTEGER,
                resonance_days INTEGER DEFAULT 1,
                encounters_count INTEGER DEFAULT 1,
                reactions_json TEXT DEFAULT '{}',
                lat REAL,
                lon REAL,
                updated_at REAL
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS listening_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT,
                artist TEXT,
                title TEXT,
                persona_title TEXT,
                zone_name TEXT,
                played_at TEXT,
                timestamp REAL DEFAULT 0
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS capsules (
                id TEXT PRIMARY KEY,
                creator_id TEXT,
                artist TEXT,
                title TEXT,
                mood_label TEXT,
                persona_title TEXT,
                zone_name TEXT,
                secret_message TEXT,
                distance_meters INTEGER,
                angle_degrees REAL,
                distance_ratio REAL,
                compatibility_percent INTEGER,
                is_unlocked INTEGER DEFAULT 1,
                lat REAL,
                lon REAL,
                is_venue INTEGER DEFAULT 0,
                venue_name TEXT DEFAULT '',
                created_at REAL
            )
        """)
        cur.execute("CREATE INDEX IF NOT EXISTS idx_devices_updated_at ON devices(updated_at);")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_capsules_created_at ON capsules(created_at);")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_history_device_id ON listening_history(device_id);")
        conn.commit()

        cur.execute("PRAGMA table_info(devices)")
        existing_cols = {row[1] for row in cur.fetchall()}
        cols_to_add = {
            "source_name": "TEXT DEFAULT 'ЭХО'",
            "persona_title": "TEXT DEFAULT ''",
            "persona_observation": "TEXT DEFAULT ''",
            "mood_label": "TEXT DEFAULT ''",
            "color_hex": "INTEGER DEFAULT 4281843192",
            "avatar_petals": "INTEGER DEFAULT 6",
            "zone_name": "TEXT DEFAULT 'Вечерняя улица'",
            "distance_meters": "INTEGER DEFAULT 60",
            "angle_degrees": "REAL DEFAULT 45.0",
            "distance_ratio": "REAL DEFAULT 0.45",
            "compatibility_percent": "INTEGER DEFAULT 85",
            "resonance_days": "INTEGER DEFAULT 1",
            "encounters_count": "INTEGER DEFAULT 1",
            "reactions_json": "TEXT DEFAULT '{}'",
            "lat": "REAL DEFAULT 55.7602",
            "lon": "REAL DEFAULT 37.6185",
            "updated_at": "REAL DEFAULT 0"
        }
        for cname, ctype in cols_to_add.items():
            if cname not in existing_cols:
                try:
                    cur.execute(f"ALTER TABLE devices ADD COLUMN {cname} {ctype}")
                except Exception as e:
                    logger.debug("ALTER TABLE devices %s: %s", cname, e)

        cur.execute("PRAGMA table_info(listening_history)")
        hist_cols = {row[1] for row in cur.fetchall()}
        if "timestamp" not in hist_cols:
            try:
                cur.execute("ALTER TABLE listening_history ADD COLUMN timestamp REAL DEFAULT 0")
            except Exception as e:
                logger.debug("ALTER TABLE listening_history timestamp: %s", e)

        cur.execute("PRAGMA table_info(capsules)")
        cap_cols = {row[1] for row in cur.fetchall()}
        if "lat" not in cap_cols:
            try:
                cur.execute("ALTER TABLE capsules ADD COLUMN lat REAL DEFAULT 55.7602")
                cur.execute("ALTER TABLE capsules ADD COLUMN lon REAL DEFAULT 37.6185")
            except Exception as e:
                logger.debug("ALTER TABLE capsules lat/lon: %s", e)
        if "is_venue" not in cap_cols:
            try:
                cur.execute("ALTER TABLE capsules ADD COLUMN is_venue INTEGER DEFAULT 0")
                cur.execute("ALTER TABLE capsules ADD COLUMN venue_name TEXT DEFAULT ''")
            except Exception as e:
                logger.debug("ALTER TABLE capsules is_venue/venue_name: %s", e)
        conn.commit()

        cur.execute("SELECT COUNT(*) FROM devices")
        if cur.fetchone()[0] == 0:
            seed_default_devices(cur, time.time())
            conn.commit()

        cur.execute("SELECT COUNT(*) FROM capsules")
        if cur.fetchone()[0] == 0:
            now = time.time()
            c1_lat, c1_lon = get_zone_jittered_coords("Вечерняя улица", "cap1")
            c2_lat, c2_lon = get_zone_jittered_coords("Вечерняя улица", "cap2")
            c3_lat, c3_lon = get_zone_jittered_coords("Сретенка · Камерные бары", "cap3")
            seed_caps = [
                (
                    "cap_seed_01", "echo_curator", "Кино", "Спокойная ночь",
                    "Холодная акустика", "Проводник туманов", "Вечерняя улица",
                    "Слушай этот трек, когда огни фонарей начнут двоиться в мокром стекле...",
                    52, 50.0, 0.36, 95, 1, c1_lat, c1_lon, 0, "", now
                ),
                (
                    "cap_seed_02", "echo_curator", "Sirotkin", "Выше домов",
                    "Теплый винил", "Архивариус забытых кассет", "Вечерняя улица",
                    "Остановись на перекрестке и подними глаза к крышам домов.",
                    95, 215.0, 0.55, 91, 1, c2_lat, c2_lon, 0, "", now
                ),
                (
                    "cap_venue_03", "venue_vinyl_lab", "Massive Attack", "Teardrop",
                    "Аналоговый трип-хоп", "Виниловая лавка «Фонограф»", "Сретенка · Камерные бары",
                    "Тайник от «Фонографа»: назови бариста кодовое слово «ЭХО-33» и получи чашку фильтра к пластинке.",
                    70, 160.0, 0.42, 97, 1, c3_lat, c3_lon, 1, "Виниловая лавка «Фонограф»", now
                )
            ]
            cur.executemany("""
                INSERT INTO capsules (
                    id, creator_id, artist, title, mood_label, persona_title,
                    zone_name, secret_message, distance_meters, angle_degrees,
                    distance_ratio, compatibility_percent, is_unlocked, lat, lon,
                    is_venue, venue_name, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, seed_caps)
            conn.commit()

        conn.close()


def lookup_metadata(artist: str, title: str):
    key = f"{artist.strip().lower()}::{title.strip().lower()}"
    if key in METADATA_CACHE:
        return METADATA_CACHE[key]

    meta = {"album": "", "genre": ""}
    query = urllib.parse.quote(f"{artist} {title}".strip())
    url = f"https://itunes.apple.com/search?term={query}&entity=song&limit=1&country=RU"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "EchoRadar/3.0"})
        with urllib.request.urlopen(req, timeout=2.0) as resp:
            data = json.loads(resp.read().decode("utf-8", errors="ignore"))
            items = data.get("results", [])
            if items:
                meta["album"] = items[0].get("collectionName", "")
                meta["genre"] = items[0].get("primaryGenreName", "")
    except Exception as e:
        logger.debug("lookup_metadata %s - %s: %s", artist, title, e)

    METADATA_CACHE[key] = meta
    return meta


def get_recent_tracks_for_device(device_id: str, limit: int = 5):
    try:
        with DB_LOCK:
            conn = get_db_connection()
            cur = conn.cursor()
            cur.execute(
                "SELECT artist, title FROM listening_history WHERE device_id = ? ORDER BY id DESC LIMIT ?",
                (device_id, limit)
            )
            rows = cur.fetchall()
            conn.close()
            return [f"{r[0]} — {r[1]}" for r in rows]
    except Exception as e:
        logger.debug("get_recent_tracks_for_device %s: %s", device_id, e)
        return []


def generate_persona_via_gemini(api_key: str, artist: str, title: str, album: str, genre: str, recent_tracks: list):
    prompt_text = (
        f"Ты — чуткий музыкальный куратор и поэт атмосферного приложения 'ЭХО'.\n"
        f"Твоя задача — создать глубокий, естественный и уникальный литературный образ слушателя без механических штампов.\n"
        f"Текущий трек: '{artist} — {title}'.\n"
        f"Альбом: '{album or 'Сингл'}'. Жанр: '{genre or 'Инди/Альтернатива'}'.\n"
        f"Недавние прослушивания за вечер: {recent_tracks or 'Начало пути'}.\n\n"
        f"Верни СТРОГО JSON-объект со следующими полями без лишних символов и без markdown-разметки:\n"
        f"{{\n"
        f'  "personaTitle": "Поэтичный и естественный образ из 2-3 слов на русском языке (например: Архивариус забытых кассет, Проводник туманов, Хранитель северных лесов, Меланхоличный космонавт, Ловец виниловых волн, Странник сонных набережных)",\n'
        f'  "moodLabel": "Музыкальное настроение из 2-3 слов (например: Теплый винил, Холодный пост-панк, Северный эмбиент, Ночной автобан, Ламповый дрим)",\n'
        f'  "personaObservation": "1-2 глубоких предложения о динамике звука в вечернем городе",\n'
        f'  "colorHex": 4281843192,\n'
        f'  "avatarPetals": 6,\n'
        f'  "compatibilityPercent": 92\n'
        f"}}"
    )

    models_to_try = ["gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro"]
    for model_name in models_to_try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key.strip()}"
        req_data = {
            "contents": [{"parts": [{"text": prompt_text}]}],
            "generationConfig": {"temperature": 0.7, "maxOutputTokens": 450}
        }
        body_bytes = json.dumps(req_data).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=body_bytes,
            headers={"Content-Type": "application/json"}
        )
        try:
            with urllib.request.urlopen(req, timeout=4.5) as resp:
                resp_json = json.loads(resp.read().decode("utf-8", errors="ignore"))
                candidates = resp_json.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        raw_text = parts[0].get("text", "").strip()
                        cleaned = re.sub(r"```json|```", "", raw_text).strip()
                        data = json.loads(cleaned)
                        return {
                            "personaTitle": str(data.get("personaTitle", "Архивариус забытых кассет")),
                            "moodLabel": str(data.get("moodLabel", "Теплый винил")),
                            "personaObservation": str(data.get("personaObservation", "Слушает музыку в вечерней тишине.")),
                            "colorHex": ensure_opaque_color(int(data.get("colorHex", 0xFF38BDF8))),
                            "avatarPetals": int(data.get("avatarPetals", 6)),
                            "compatibilityPercent": int(data.get("compatibilityPercent", 90))
                        }
        except Exception as e:
            logger.warning("Gemini model %s failed: %s", model_name, e)
            continue
    return None


def generate_curated_persona(artist: str, title: str, device_id: str, incoming_recent_tracks: list = None):
    cache_key = f"{artist.strip().lower()}::{title.strip().lower()}"
    if cache_key in AI_PERSONA_CACHE:
        return AI_PERSONA_CACHE[cache_key]

    meta = lookup_metadata(artist, title)
    album = meta.get("album", "")
    genre = meta.get("genre", "")
    recent_tracks = incoming_recent_tracks or get_recent_tracks_for_device(device_id, limit=4)

    # 1. Поиск в Золотом Фонде и Жанровой Матрице библиотеки ЭХО (0ms отклик)
    matched = find_archetype_in_library(artist, album, genre)
    if matched:
        obs = matched.get("personaObservation", "")
        if not obs:
            obs = f"Слушает {matched.get('moodLabel', 'музыку')} в вечерней тишине."
        if len(recent_tracks) > 1:
            obs = f"{obs} [Траектория вечера: {len(recent_tracks)} трека]"
        result = {
            "personaTitle": matched["personaTitle"],
            "moodLabel": matched["moodLabel"],
            "personaObservation": obs,
            "colorHex": ensure_opaque_color(int(matched.get("colorHex", 0xFF38BDF8))),
            "avatarPetals": int(matched.get("avatarPetals", 6)),
            "compatibilityPercent": int(matched.get("compatibilityPercent", 92))
        }
        AI_PERSONA_CACHE[cache_key] = result
        return result

    # 2. Если в библиотеке нет точного совпадения — генерация через Gemini и автосохранение
    api_key = SERVER_CONFIG.get("gemini_api_key", "").strip()
    if api_key:
        ai_res = generate_persona_via_gemini(api_key, artist, title, album, genre, recent_tracks)
        if ai_res:
            AI_PERSONA_CACHE[cache_key] = ai_res
            save_key = f"{artist.strip().lower()}::{album.strip().lower()}" if album else artist.strip().lower()
            save_curated_record_to_library(save_key, ai_res)
            return ai_res

    # 3. Эстетический фоллбэк: берем органический архетип
    seed_str = f"{artist.lower().strip()}|{title.lower().strip()}|{album.lower().strip()}"
    digest = hashlib.sha256(seed_str.encode("utf-8")).digest()
    idx = digest[0] % len(ORGANIC_PERSONAS)
    title_base, mood_base, col_base, obs_base = ORGANIC_PERSONAS[idx]

    petals = 5 + (digest[1] % 5)
    compat = 78 + (digest[2] % 21)

    obs = obs_base
    if len(recent_tracks) > 1:
        obs = f"{obs} [Траектория вечера: путь сквозь {len(recent_tracks)} композиции к ночному созерцанию]"
    elif genre:
        obs = f"{obs} [Вайб: {genre}]"

    result = {
        "personaTitle": title_base,
        "moodLabel": mood_base,
        "personaObservation": obs,
        "colorHex": ensure_opaque_color(col_base),
        "avatarPetals": petals,
        "compatibilityPercent": compat
    }
    AI_PERSONA_CACHE[cache_key] = result
    return result


def record_history_if_unique(cur, device_id: str, artist: str, title: str, persona_title: str, zone_name: str):
    cur.execute(
        "SELECT artist, title FROM listening_history WHERE device_id = ? ORDER BY id DESC LIMIT 1",
        (device_id,)
    )
    last = cur.fetchone()
    if last and last[0].strip().lower() == artist.strip().lower() and last[1].strip().lower() == title.strip().lower():
        return

    now_ts = time.time()
    now_str = datetime.now().strftime("%H:%M")
    cur.execute(
        """
        INSERT INTO listening_history (device_id, artist, title, persona_title, zone_name, played_at, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (device_id, artist, title, persona_title, zone_name, now_str, now_ts)
    )
    try:
        cur.execute("DELETE FROM listening_history WHERE id NOT IN (SELECT id FROM listening_history ORDER BY id DESC LIMIT 500)")
    except Exception as e:
        logger.debug("prune history error: %s", e)


def build_state_payload(for_device_id: str = None):
    with DB_LOCK:
        conn = get_db_connection()
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()

        now_sec = time.time()

        # Автоматическая очистка старых треков старше 90 минут (кроме постоянных маяков)
        cutoff_time = now_sec - (90 * 60)
        cur.execute("DELETE FROM devices WHERE updated_at < ? AND id NOT LIKE 'sig_%'", (cutoff_time,))
        cur.execute("UPDATE devices SET updated_at = ? WHERE id LIKE 'sig_%' AND updated_at < ?", (now_sec - 120, cutoff_time))
        conn.commit()

        cur.execute("SELECT COUNT(*) FROM devices")
        if cur.fetchone()[0] == 0:
            seed_default_devices(cur, now_sec)
            conn.commit()

        cur.execute("SELECT * FROM devices ORDER BY updated_at DESC LIMIT 35")
        dev_rows = cur.fetchall()

        signals = []
        zone_buckets = {}
        my_device_record = None

        for row in dev_rows:
            dev_id = row["id"]
            if for_device_id and dev_id == for_device_id:
                my_device_record = row

            try:
                reactions = json.loads(row["reactions_json"] or "{}")
            except Exception as e:
                logger.debug("reactions_json parse error for %s: %s", row["id"], e)
                reactions = {}

            updated_at = float(row["updated_at"] or 0)
            age_min = max(0, int((now_sec - updated_at) / 60.0))
            is_ghost = age_min > 5

            compat_num = int(row["compatibility_percent"] or 88)
            encounters_num = int(row["encounters_count"] or 1)
            if compat_num >= 85:
                res_story = "Редкое созвучие: вы оба растворяетесь в одном звуковом спектре."
            elif compat_num >= 70:
                res_story = "Тёплый вечерний резонанс: ваши звуковые траектории пересеклись в этой точке города."
            else:
                res_story = "Случайная встреча контрастов в ночном эфире."
            if encounters_num > 1:
                res_story += f" Ваши волны встречаются уже {encounters_num}-й раз за неделю."

            sig = {
                "id": dev_id,
                "artist": row["artist"],
                "title": row["title"],
                "resonanceStory": res_story,
                "sourceName": row["source_name"] or "ЭХО",
                "distanceMeters": int(row["distance_meters"] or 50),
                "angleDegrees": float(row["angle_degrees"] or 45.0),
                "distanceRatio": float(row["distance_ratio"] or 0.45),
                "personaTitle": row["persona_title"] or "Архивариус забытых кассет",
                "personaObservation": (
                    f"Призрачный след: звучал {age_min} мин назад. {row['persona_observation']}"
                    if is_ghost else (row["persona_observation"] or "Слушает музыку в вечерней тишине.")
                ),
                "moodLabel": (f"Призрачный след ({age_min} мин)" if is_ghost else (row["mood_label"] or "Теплый винил")),
                "compatibilityPercent": int(row["compatibility_percent"] or 88),
                "resonanceDays": int(row["resonance_days"] or 1),
                "encountersCount": int(row["encounters_count"] or 1),
                "colorHex": ensure_opaque_color(int(row["color_hex"] or 0xFF38BDF8)),
                "avatarPetals": int(row["avatar_petals"] or 6),
                "zoneName": row["zone_name"] or "Вечерняя улица",
                "lat": float(row["lat"] or 55.7602),
                "lon": float(row["lon"] or 37.6185),
                "updatedAt": updated_at,
                "isGhost": is_ghost,
                "ageMinutes": age_min,
                "reactions": reactions
            }
            signals.append(sig)
            zone_buckets.setdefault(sig["zoneName"], []).append(sig)

        cur.execute("SELECT * FROM capsules ORDER BY created_at DESC LIMIT 30")
        cap_rows = cur.fetchall()
        capsules = []
        for crow in cap_rows:
            is_ven = bool(crow["is_venue"]) if "is_venue" in crow.keys() else False
            ven_name = crow["venue_name"] if "venue_name" in crow.keys() else ""
            capsules.append({
                "id": crow["id"],
                "creatorId": crow["creator_id"],
                "artist": crow["artist"],
                "title": crow["title"],
                "moodLabel": crow["mood_label"],
                "personaTitle": crow["persona_title"],
                "zoneName": crow["zone_name"],
                "secretMessage": crow["secret_message"],
                "distanceMeters": int(crow["distance_meters"] or 65),
                "angleDegrees": float(crow["angle_degrees"] or 120.0),
                "distanceRatio": float(crow["distance_ratio"] or 0.5),
                "compatibilityPercent": int(crow["compatibility_percent"] or 90),
                "isUnlocked": bool(crow["is_unlocked"]),
                "lat": float(crow["lat"] or 55.7602),
                "lon": float(crow["lon"] or 37.6185),
                "isVenue": is_ven,
                "venueName": ven_name,
                "createdAt": float(crow["created_at"] or 0)
            })

        cur.execute("SELECT artist, title, persona_title, zone_name, played_at FROM listening_history ORDER BY id DESC LIMIT 15")
        hist_rows = cur.fetchall()
        evening_traces = []
        for h in hist_rows:
            evening_traces.append(
                f"[{h['played_at']}] {h['persona_title']} ({h['zone_name']}): {h['artist']} — {h['title']}"
            )

        if not evening_traces:
            evening_traces = [
                "В эфире зажглись первые вечерние маяки.",
                "Сонар сканирует виниловые ауры в радиусе тишины..."
            ]

        city_zones = []
        for z in CITY_ZONES:
            zname = z["placeName"]
            bucket = zone_buckets.get(zname, [])
            center_coords = ZONE_COORDINATES.get(zname, DEFAULT_COORDS)

            if bucket:
                artists_str = ", ".join(list(dict.fromkeys(b["artist"] for b in bucket))[:4])
                vibe_str = bucket[0]["moodLabel"]
                dominant_color = bucket[0]["colorHex"]
                count_val = len(bucket) + z["listenersCount"]
                fog_radius = min(750, 480 + (len(bucket) * 60))
            else:
                artists_str = z["recentArtists"]
                vibe_str = z["dominantVibe"]
                dominant_color = z.get("defaultColor", 0xFF38BDF8)
                count_val = z["listenersCount"]
                fog_radius = 450

            city_zones.append({
                "placeName": zname,
                "dominantVibe": vibe_str,
                "recentArtists": artists_str,
                "listenersCount": count_val,
                "lat": center_coords[0],
                "lon": center_coords[1],
                "fogColorHex": ensure_opaque_color(dominant_color),
                "fogRadius": fog_radius
            })

        with REACTIONS_LOCK:
            reactions_snapshot = list(RECENT_REACTIONS)

        active_banner = None
        for r in reversed(reactions_snapshot):
            if now_sec - r["time"] < 30.0:
                if for_device_id:
                    if r.get("targetId") == for_device_id:
                        active_banner = {
                            "id": r["id"],
                            "targetId": r["targetId"],
                            "senderId": r["senderId"],
                            "symbol": r["symbol"],
                            "message": r["message"],
                            "time": r["time"]
                        }
                        break
                else:
                    active_banner = {
                        "id": r["id"],
                        "targetId": r["targetId"],
                        "senderId": r["senderId"],
                        "symbol": r["symbol"],
                        "message": r["message"],
                        "time": r["time"]
                    }
                    break

        my_reactions = {}
        my_refined_mood = ""
        my_curated_persona = ""
        my_curated_obs = ""
        my_color_hex = 0xFF38BDF8

        if my_device_record:
            try:
                my_reactions = json.loads(my_device_record["reactions_json"] or "{}")
            except Exception as e:
                logger.debug("my_reactions parse error: %s", e)
                my_reactions = {}
            my_refined_mood = my_device_record["mood_label"] or ""
            my_curated_persona = my_device_record["persona_title"] or ""
            my_curated_obs = my_device_record["persona_observation"] or ""
            my_color_hex = ensure_opaque_color(int(my_device_record["color_hex"] or 0xFF38BDF8))

        conn.close()

    return {
        "status": "ok",
        "serverTime": int(now_sec),
        "signals": signals,
        "capsules": capsules,
        "eveningTraces": evening_traces,
        "cityZones": city_zones,
        "incomingBanner": active_banner,
        "myReactions": my_reactions,
        "myRefinedMood": my_refined_mood,
        "myCuratedPersona": my_curated_persona,
        "myCuratedObservation": my_curated_obs,
        "myColorHex": my_color_hex
    }


def _load_template(name: str) -> str:
    tpl_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates", name)
    try:
        with open(tpl_path, "r", encoding="utf-8") as f:
            return f.read()
    except Exception as e:
        logger.warning("Template %s not found: %s", name, e)
        return f"<h1>Template {name} not found</h1>"

DASHBOARD_HTML = _load_template("dashboard.html")
PWA_HTML = _load_template("pwa.html")





class EchoRequestHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def _send_json(self, payload, status=200):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept, X-Echo-Token")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _send_html(self, html_text, status=200):
        raw = html_text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        token = SERVER_CONFIG.get("echo_token", "")
        if token:
            self.send_header("Set-Cookie", f"echo_token={token}; Path=/; SameSite=Lax")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _check_token(self) -> bool:
        """Проверяет токен для POST-запросов (X-Echo-Token заголовок или cookie от веб-интерфейса)."""
        expected = SERVER_CONFIG.get("echo_token", "")
        if not expected:
            return True  # Если токен не настроен — пропускаем проверку
        provided = self.headers.get("X-Echo-Token", "") or self.headers.get("X-API-Key", "")
        if provided == expected:
            return True
        client_ip = getattr(self, "client_address", [""])[0]
        if client_ip in ("127.0.0.1", "::1", "localhost"):
            return True
        cookie_header = self.headers.get("Cookie", "")
        if f"echo_token={expected}" in cookie_header:
            return True
        return False

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept, X-Echo-Token")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/api/state":
            query = urllib.parse.parse_qs(parsed.query)
            dev_id = query.get("deviceId", [None])[0] or self.headers.get("X-Device-Id")
            self._send_json(build_state_payload(for_device_id=dev_id))
        elif path in ("/", "/index.html"):
            self._send_html(DASHBOARD_HTML)
        elif path in ("/app", "/pwa"):
            self._send_html(PWA_HTML)
        else:
            self._send_json({"status": "ok", "endpoint": path})

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        # Защищённые эндпоинты требуют токен
        PROTECTED_PATHS = {"/api/signal", "/api/react", "/api/drop_capsule",
                           "/api/delete_signal", "/api/save_config", "/api/bump_encounter"}
        if path in PROTECTED_PATHS and not self._check_token():
            self._send_json({"status": "error", "message": "Unauthorized"}, status=401)
            return

        length = int(self.headers.get("Content-Length", 0))
        body_raw = self.rfile.read(length).decode("utf-8", errors="ignore") if length > 0 else "{}"
        try:
            data = json.loads(body_raw)
        except Exception as e:
            logger.debug("do_POST body parse error: %s", e)
            data = {}

        if path == "/api/signal":
            dev_id = str(data.get("id") or f"dev_{int(time.time())}")
            artist = str(data.get("artist") or "Неизвестный артист").strip()
            title = str(data.get("title") or "Без названия").strip()
            source_name = str(data.get("sourceName") or "Android ЭХО")
            zone_name = str(data.get("zoneName") or "Вечерняя улица")

            incoming_recent = data.get("recentTracks", [])
            future = _PERSONA_EXECUTOR.submit(generate_curated_persona, artist, title, dev_id, incoming_recent)
            try:
                curated = future.result(timeout=5.0)
            except Exception as e:
                logger.warning("persona future timed out: %s", e)
                curated = generate_curated_persona.__wrapped__ if hasattr(generate_curated_persona, '__wrapped__') else {
                    "personaTitle": "Архивариус забытых кассет",
                    "moodLabel": "Теплый винил",
                    "personaObservation": "Слушает музыку в вечерней тишине.",
                    "colorHex": 0xFF38BDF8,
                    "avatarPetals": 6,
                    "compatibilityPercent": 88
                }
            persona_title = str(curated["personaTitle"])
            persona_obs = str(curated["personaObservation"])
            mood_label = str(curated["moodLabel"])
            color_hex = ensure_opaque_color(int(curated["colorHex"]))
            avatar_petals = int(curated["avatarPetals"])
            compat = int(curated["compatibilityPercent"])

            h_val = int(hashlib.md5(dev_id.encode("utf-8")).hexdigest()[:8], 16)
            dist_meters = int(data.get("distanceMeters") or (25 + (h_val % 180)))
            angle_deg = float(data.get("angleDegrees") or (h_val % 360))
            dist_ratio = float(data.get("distanceRatio") or min(0.88, max(0.22, dist_meters / 220.0)))

            lat, lon = get_zone_jittered_coords(zone_name, dev_id)

            now = time.time()
            with DB_LOCK:
                conn = get_db_connection()
                cur = conn.cursor()

                cur.execute("SELECT encounters_count, resonance_days, reactions_json FROM devices WHERE id = ?", (dev_id,))
                existing = cur.fetchone()
                enc_count = existing[0] if existing else 1
                res_days = existing[1] if existing else 1
                reactions_json = existing[2] if existing else "{}"

                cur.execute("""
                    INSERT OR REPLACE INTO devices (
                        id, artist, title, source_name, persona_title, persona_observation,
                        mood_label, color_hex, avatar_petals, zone_name, distance_meters,
                        angle_degrees, distance_ratio, compatibility_percent, resonance_days,
                        encounters_count, reactions_json, lat, lon, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    dev_id, artist, title, source_name, persona_title, persona_obs,
                    mood_label, color_hex, avatar_petals, zone_name, dist_meters,
                    angle_deg, dist_ratio, compat, res_days, enc_count, reactions_json,
                    lat, lon, now
                ))

                record_history_if_unique(cur, dev_id, artist, title, persona_title, zone_name)
                conn.commit()
                conn.close()

            logger.info("📡 СИГНАЛ [%s] %s: %s — %s (%s • %s)", zone_name, dev_id, artist, title, persona_title, mood_label)
            payload = build_state_payload(for_device_id=dev_id)
            self._send_json(payload)

        elif path == "/api/save_config":
            k = str(data.get("gemini_api_key", "")).strip()
            SERVER_CONFIG["gemini_api_key"] = k
            save_config(SERVER_CONFIG)
            logger.info("⚙️ Ключ ИИ-куратора сохранен (%s).", 'Активен' if k else 'Сброшен')
            self._send_json({"status": "ok", "has_key": bool(k)})

        elif path == "/api/react":
            target_id = str(data.get("targetId") or data.get("signalId") or "")
            sender_id = str(data.get("senderId") or "unknown_sender")
            symbol = str(data.get("symbol") or "✦")
            rec_id = int(time.time() * 1000)
            msg = f"Вам отправлен импульс {symbol}"

            with REACTIONS_LOCK:
                RECENT_REACTIONS.append({
                    "id": rec_id,
                    "targetId": target_id,
                    "senderId": sender_id,
                    "symbol": symbol,
                    "message": msg,
                    "time": time.time()
                })
                if len(RECENT_REACTIONS) > 30:
                    del RECENT_REACTIONS[:-30]

            with DB_LOCK:
                conn = get_db_connection()
                cur = conn.cursor()
                cur.execute("SELECT reactions_json FROM devices WHERE id = ?", (target_id,))
                row = cur.fetchone()
                if row:
                    try:
                        r_map = json.loads(row[0] or "{}")
                    except Exception as e:
                        logger.debug("react r_map parse error: %s", e)
                        r_map = {}
                    r_map[symbol] = int(r_map.get(symbol, 0)) + 1
                    cur.execute(
                        "UPDATE devices SET reactions_json = ?, updated_at = ? WHERE id = ?",
                        (json.dumps(r_map, ensure_ascii=False), time.time(), target_id)
                    )
                    conn.commit()
                conn.close()

            logger.info("✨ ОТКЛИК %s от %s -> %s", symbol, sender_id, target_id)
            self._send_json(build_state_payload(for_device_id=sender_id))

        elif path == "/api/drop_capsule":
            cap_id = f"cap_{int(time.time() * 1000)}"
            creator_id = str(data.get("creatorId") or "local_user")
            artist = str(data.get("artist") or "Sirotkin")
            title = str(data.get("title") or "Август")
            zone_name = str(data.get("zoneName") or "Вечерняя улица")
            secret_msg = str(data.get("secretMessage") or "Слушай этот момент здесь...")
            is_venue = 1 if data.get("isVenue") else 0
            venue_name = str(data.get("venueName") or "").strip()

            curated = generate_curated_persona(artist, title, creator_id)
            mood_label = str(data.get("moodLabel") or curated["moodLabel"])
            persona_title = venue_name if is_venue and venue_name else str(data.get("personaTitle") or curated["personaTitle"])

            angle_deg = float(random.randint(15, 345))
            dist_meters = random.randint(25, 140)
            dist_ratio = round(min(0.85, max(0.24, dist_meters / 180.0)), 2)

            lat, lon = get_zone_jittered_coords(zone_name, cap_id)

            with DB_LOCK:
                conn = get_db_connection()
                cur = conn.cursor()
                cur.execute("""
                    INSERT INTO capsules (
                        id, creator_id, artist, title, mood_label, persona_title,
                        zone_name, secret_message, distance_meters, angle_degrees,
                        distance_ratio, compatibility_percent, is_unlocked, lat, lon,
                        is_venue, venue_name, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                """, (
                    cap_id, creator_id, artist, title, mood_label, persona_title,
                    zone_name, secret_msg, dist_meters, angle_deg,
                    dist_ratio, curated["compatibilityPercent"], lat, lon,
                    is_venue, venue_name, time.time()
                ))
                conn.commit()
                conn.close()

            log_tag = f"🏛️ ТАЙНИК ЛОКАЦИИ [{venue_name}]" if is_venue else "⏳ ТАЙНИК"
            logger.info("%s [%s] %s — %s: «%s»", log_tag, zone_name, artist, title, secret_msg)
            self._send_json(build_state_payload(for_device_id=creator_id))

        elif path == "/api/bump_encounter":
            target_id = str(data.get("targetId") or "")
            sender_id = str(data.get("senderId") or "")
            with DB_LOCK:
                conn = get_db_connection()
                cur = conn.cursor()
                cur.execute(
                    "UPDATE devices SET encounters_count = encounters_count + 1 WHERE id = ?",
                    (target_id,)
                )
                conn.commit()
                conn.close()
            logger.info("🤝 ПЕРЕСЕЧЕНИЕ с %s зафиксировано в SQLite", target_id)
            self._send_json(build_state_payload(for_device_id=sender_id))

        elif path == "/api/delete_signal":
            target_id = str(data.get("targetId") or data.get("id") or "")
            with DB_LOCK:
                conn = get_db_connection()
                cur = conn.cursor()
                cur.execute("DELETE FROM devices WHERE id = ?", (target_id,))
                conn.commit()
                conn.close()
            logger.info("🗑️ СИГНАЛ %s удален из эфира", target_id)
            self._send_json(build_state_payload())

        else:
            self._send_json(build_state_payload())


def start_udp_broadcast():
    def _broadcaster():
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        payload = f"ECHO_SERVER_DISCOVERY:{HTTP_PORT}".encode("utf-8")
        while True:
            try:
                sock.sendto(payload, ("255.255.255.255", UDP_DISCOVERY_PORT))
            except Exception as e:
                logger.debug("UDP broadcast error: %s", e)
            time.sleep(3.0)

    t = threading.Thread(target=_broadcaster, daemon=True)
    t.start()



def start_windows_media_listener():
    """Автоматический перехват играющей на Windows музыки (Яндекс Музыка / Spotify / Chrome / VK)
    через Windows SMTC. Мгновенно передает ПК-трек в эфир как маяк pc_studio_node."""
    def _worker():
        try:
            import asyncio
            from winrt.windows.media.control import GlobalSystemMediaTransportControlsSessionManager
        except Exception as e:
            logger.info("ℹ️ Windows SMTC недоступен: %s", e)
            return

        logger.info("🎧 Автоперехватчик музыки с ПК (Windows SMTC) активен!")
        last_track = ("", "")
        last_update = 0.0
        last_logged = ("", "")

        async def _check_media():
            nonlocal last_track, last_update, last_logged
            try:
                manager = await GlobalSystemMediaTransportControlsSessionManager.request_async()
                session = manager.get_current_session()
                if not session:
                    return

                props = await session.try_get_media_properties_async()
                if not props:
                    return

                artist = (props.artist or "").strip()
                title = (props.title or "").strip()
                app_id = session.source_app_user_model_id or "ПК плеер"
                app_name = app_id.split(".")[-1].split("!")[-1] or "Windows"

                if not artist and not title:
                    return

                curr = (artist, title)
                now = time.time()

                if curr != last_track or (now - last_update > 15.0):
                    last_track = curr
                    last_update = now

                    dev_id = "pc_studio_node"
                    zone_name = "Вечерняя улица"
                    lat, lon = get_zone_jittered_coords(zone_name, dev_id)

                    future = _PERSONA_EXECUTOR.submit(generate_curated_persona, artist, title, dev_id, [])
                    try:
                        curated = future.result(timeout=4.0)
                    except Exception:
                        curated = {
                            "personaTitle": "Архивариус забытых кассет",
                            "moodLabel": "Теплый винил",
                            "personaObservation": f"Слушает {artist} через {app_name} на вечернем компьютере.",
                            "colorHex": 0xFF38BDF8,
                            "avatarPetals": 6,
                            "compatibilityPercent": 94
                        }

                    with DB_LOCK:
                        conn = get_db_connection()
                        cur = conn.cursor()
                        cur.execute("SELECT encounters_count, resonance_days, reactions_json FROM devices WHERE id = ?", (dev_id,))
                        existing = cur.fetchone()
                        enc_count = existing[0] if existing else 1
                        res_days = existing[1] if existing else 1
                        reactions_json = existing[2] if existing else "{}"

                        cur.execute("""
                            INSERT OR REPLACE INTO devices (
                                id, artist, title, source_name, persona_title, persona_observation,
                                mood_label, color_hex, avatar_petals, zone_name, distance_meters,
                                angle_degrees, distance_ratio, compatibility_percent, resonance_days,
                                encounters_count, reactions_json, lat, lon, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, (
                            dev_id, artist, title, f"ПК ({app_name})",
                            curated["personaTitle"], curated["personaObservation"],
                            curated["moodLabel"], ensure_opaque_color(int(curated["colorHex"])),
                            int(curated["avatarPetals"]), zone_name, 28,
                            42.0, 0.28, int(curated["compatibilityPercent"]), res_days, enc_count, reactions_json,
                            lat, lon, now
                        ))
                        conn.commit()
                        conn.close()

                    if curr != last_logged:
                        last_logged = curr
                        logger.info("📡 СИГНАЛ ПК [%s] %s: %s — %s (%s)", zone_name, dev_id, artist, title, app_name)
            except Exception as ex:
                logger.debug("SMTC check error: %s", ex)

        while True:
            try:
                import asyncio
                asyncio.run(_check_media())
            except Exception as e:
                logger.debug("SMTC loop error: %s", e)
            time.sleep(2.5)

    t = threading.Thread(target=_worker, daemon=True)
    t.start()


class EchoServer(ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        # Подавляем шумные разрывы соединений от мобильных клиентов (WinError 10054 / ConnectionResetError)
        ex_type, ex_val, _ = sys.exc_info()
        if ex_type in (ConnectionResetError, BrokenPipeError) or (isinstance(ex_val, OSError) and getattr(ex_val, "winerror", None) == 10054):
            return
        super().handle_error(request, client_address)

def main():
    init_db()
    start_udp_broadcast()
    start_windows_media_listener()
    local_ip = get_local_ip()
    echo_token = SERVER_CONFIG.get("echo_token", "")
    server = EchoServer(("0.0.0.0", HTTP_PORT), EchoRequestHandler)
    logger.info("=" * 66)
    logger.info(" 📡 ЭХО (Echo Radar) — Ночной акустический атлас запущен!")
    logger.info(" 🌐 Локальный адрес в сети : http://%s:%d", local_ip, HTTP_PORT)
    logger.info(" 📱 Сенсорный Сонар и Карта : http://%s:%d/app", local_ip, HTTP_PORT)
    logger.info(" ⚙️  Пульт управления и ИИ  : http://%s:%d/", local_ip, HTTP_PORT)
    if echo_token:
        logger.info(" 🔑 Echo Token (для Android) : %s", echo_token)
    logger.info("=" * 66)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("\nСервер ЭХО остановлен.")
        server.server_close()


if __name__ == "__main__":
    main()
