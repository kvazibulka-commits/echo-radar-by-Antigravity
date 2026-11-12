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

from __future__ import annotations

import base64
import concurrent.futures
import hashlib
import hmac
import ipaddress
import json
import logging
import math
import os
import random
import re
import secrets
import socket
import sqlite3
import sys
import threading
import time
import urllib.parse
import urllib.request
from collections import OrderedDict, deque
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SERVER_VERSION = "3.3.0"
SERVER_STARTED_AT = time.time()

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

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
HTTP_PORT = int(os.environ.get("PORT", 8080))
UDP_DISCOVERY_PORT = 8888
DB_PATH = os.environ.get("ECHO_DB_PATH") or os.path.join(BASE_DIR, "echo_memory.db")
CONFIG_PATH = os.environ.get("ECHO_CONFIG_PATH") or os.path.join(BASE_DIR, "echo_config.json")
ARCHETYPES_PATH = os.environ.get("ECHO_ARCHETYPES_PATH") or os.path.join(BASE_DIR, "echo_archetypes.json")

# --- Ограничения входных данных: защита публичного туннеля от перегрузки ---
MAX_BODY_BYTES = 2 * 1024 * 1024          # максимальный размер JSON-тела запроса
MAX_COVER_BYTES = 1_500_000               # максимальный размер обложки трека
MAX_TEXT_LEN = 160                        # максимальная длина исполнителя/названия
MAX_MESSAGE_LEN = 400                     # максимальная длина послания в тайнике
MAX_COVERS = 48                           # сколько обложек держим в памяти
STATE_RATE_LIMIT = 240                    # GET-запросов состояния в минуту с одного IP
WRITE_RATE_LIMIT = 90                     # POST-запросов в минуту с одного IP

# Признаки служебных медиа-сессий браузеров/расширений, которые не должны попадать в эфир
JUNK_MARKERS = ("://", "offscreen.html", "chrome-extension", "about:blank", "newtab")
_TAB_TITLE_RE = re.compile(r"\|\s*[^|]{1,40}\([0-9A-Fa-f]{6,}\)\s*$")

# Странные «треки» с пустым исполнителем и заголовком вкладки браузера (например,
# "Systrum Sistum | SSR (308046B0AF4A39CB)") выглядят как музыка, но музыкой не являются.
def is_junk_track(artist: str, title: str) -> bool:
    a = (artist or "").strip()
    t = (title or "").strip()
    if not t:
        return True
    blob = f"{a} {t}".lower()
    if any(marker in blob for marker in JUNK_MARKERS):
        return True
    if not a and (" | " in t or _TAB_TITLE_RE.search(t)):
        return True
    if len(t) > 200:
        return True
    return False


def sanitize_text(value, limit: int = MAX_TEXT_LEN, default: str = "") -> str:
    """Убирает управляющие символы и ограничивает длину пользовательского текста."""
    text = str(value if value is not None else default)
    text = re.sub(r"[\x00-\x1f\x7f]+", " ", text).strip()
    return text[:limit]


def normalize_zone(zone_name: str) -> str:
    """Приводит произвольное имя зоны к одной из известных зон города."""
    raw = sanitize_text(zone_name, 120)
    if not raw:
        return "Вечерняя улица"
    for zone in ZONE_COORDINATES:
        if raw == zone:
            return zone
    lowered = raw.lower()
    for zone in ZONE_COORDINATES:
        base = zone.split(" · ")[0].lower()
        if base and (base in lowered or lowered in zone.lower()):
            return zone
    return "Вечерняя улица"

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

    art_clean = (artist or "").strip().lower()
    alb_clean = (album or "").strip().lower()
    gen_clean = (genre or "").strip().lower()

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
RATE_LOCK = threading.Lock()
RATE_BUCKETS = {}                 # ip -> deque[timestamp]
EVENT_ID_LOCK = threading.Lock()
_LAST_EVENT_ID = 0
METADATA_CACHE = LRUCache(maxsize=500)
AI_PERSONA_CACHE = LRUCache(maxsize=500)
RECENT_REACTIONS = []
DEVICE_COVERS = LRUCache(maxsize=MAX_COVERS)
_PERSONA_EXECUTOR = None
GEMINI_WARNED = False


def next_event_id() -> int:
    """Монотонно возрастающий числовой id события (мс), без коллизий при параллельных запросах."""
    global _LAST_EVENT_ID
    with EVENT_ID_LOCK:
        now_ms = int(time.time() * 1000)
        _LAST_EVENT_ID = now_ms if now_ms > _LAST_EVENT_ID else _LAST_EVENT_ID + 1
        return _LAST_EVENT_ID


def rate_limit_allow(ip: str, limit: int, window: float = 60.0) -> bool:
    """Простое скользящее окно: не более limit запросов за window секунд с одного IP."""
    now = time.time()
    with RATE_LOCK:
        bucket = RATE_BUCKETS.get(ip)
        if bucket is None:
            bucket = deque()
            RATE_BUCKETS[ip] = bucket
        while bucket and now - bucket[0] > window:
            bucket.popleft()
        if len(bucket) >= limit:
            return False
        bucket.append(now)
        # Периодическая уборка пустых корзин, чтобы словарь не рос бесконечно
        if len(RATE_BUCKETS) > 512:
            for key in [k for k, v in RATE_BUCKETS.items() if not v or now - v[-1] > window]:
                RATE_BUCKETS.pop(key, None)
        return True

# --- АРГОН & CHATGPT: ИНТЕРАКТИВНЫЙ ЭФИР ---
NEEDLE_SYNCS = {}      # senderId -> {"targetId": str, "time": float}
THERMAL_PATCHES = []   # [{"id": str, "targetId": str, "senderId": str, "x": float, "y": float, "time": float}]

# --- CHATGPT: МАТЕМАТИЧЕСКАЯ МОДЕЛЬ РЕЗОНАНСА & SERENDIPITY ENGINE ---
CAMELOT_INDEX = {
    "1A": 1, "2A": 2, "3A": 3, "4A": 4, "5A": 5, "6A": 6,
    "7A": 7, "8A": 8, "9A": 9, "10A": 10, "11A": 11, "12A": 12,
    "1B": 1, "2B": 2, "3B": 3, "4B": 4, "5B": 5, "6B": 6,
    "7B": 7, "8B": 8, "9B": 9, "10B": 10, "11B": 11, "12B": 12,
}

CULTURAL_BRIDGES = {
    frozenset(["synth-pop", "minimal-techno"]): (0.92, "Аналоговые секвенции и ритмический минимализм"),
    frozenset(["post-punk", "shoegaze"]): (0.95, "Стена гитарного ревербера и меланхоличный бас"),
    frozenset(["indie", "dream-pop"]): (0.91, "Двойной винтажный хорус и теплая ностальгия"),
    frozenset(["lo-fi", "trip-hop"]): (0.94, "Виниловый кракелюр и ломаный ночной бит"),
    frozenset(["ambient", "drone"]): (0.96, "Медитативное звуковое полотно без границ"),
    frozenset(["synth-pop", "post-punk"]): (0.88, "Холодные синтезаторные линии советской и берлинской волны"),
    frozenset(["indie", "post-punk"]): (0.85, "Сырой перегруз пустых городских переулков"),
    frozenset(["techno", "ambient"]): (0.87, "Глубокая пространственная гипнопедия"),
}

def calculate_tempo_harmonic(bpm_a: float, bpm_b: float, sigma: float = 0.15) -> float:
    """ChatGPT: Октавная гармоническая совместимость темпа T_AB."""
    if bpm_a <= 0 or bpm_b <= 0:
        return 0.75
    log_ratio = math.log2(bpm_a / bpm_b)
    best = 0.0
    for n in (-1, 0, 1):
        val = math.exp(-((log_ratio - n) ** 2) / (2.0 * sigma * sigma))
        if val > best:
            best = val
    return round(best, 3)

def calculate_camelot_harmonic(k_a: str, k_b: str) -> float:
    """ChatGPT: Гармоническая совместимость по Camelot Wheel K_AB."""
    k_a = (k_a or "").strip().upper()
    k_b = (k_b or "").strip().upper()
    if not k_a or not k_b:
        return 0.80
    if k_a == k_b:
        return 1.0
    num_a = CAMELOT_INDEX.get(k_a, 8)
    num_b = CAMELOT_INDEX.get(k_b, 8)
    letter_a = k_a[-1] if k_a[-1] in ("A", "B") else "A"
    letter_b = k_b[-1] if k_b[-1] in ("A", "B") else "A"

    if num_a == num_b and letter_a != letter_b:
        return 0.95

    diff = abs(num_a - num_b)
    circle_dist = min(diff, 12 - diff)

    if letter_a == letter_b:
        if circle_dist == 1:
            return 0.90
        elif circle_dist == 2:
            return 0.65
        elif circle_dist == 3:
            return 0.45
        else:
            return 0.25
    else:
        if circle_dist == 1:
            return 0.75
        else:
            return 0.30

def calculate_mood_vector(v_a: float, a_a: float, v_b: float, a_b: float, sigma: float = 0.35) -> float:
    """ChatGPT: Эмоциональный вектор Valence & Energy M_AB."""
    dist_sq = ((v_a - v_b) ** 2) + ((a_a - a_b) ** 2)
    return round(math.exp(-0.5 * dist_sq / (sigma * sigma)), 3)

def calculate_serendipity_engine(genre_a: str, genre_b: str, base_sim: float) -> tuple[float, str]:
    """ChatGPT: Serendipity Engine через граф культурных мостов S_AB."""
    ga = (genre_a or "indie").lower().strip()
    gb = (genre_b or "indie").lower().strip()
    if ga == gb:
        return 0.0, ""
    key = frozenset([ga, gb])
    if key in CULTURAL_BRIDGES:
        bridge_score, bridge_desc = CULTURAL_BRIDGES[key]
        u_pair = 0.85
        return u_pair * bridge_score * base_sim, bridge_desc
    for pair_set, (bridge_score, bridge_desc) in CULTURAL_BRIDGES.items():
        items = list(pair_set)
        if (items[0] in ga or ga in items[0]) and (items[1] in gb or gb in items[1]):
            u_pair = 0.80
            return u_pair * bridge_score * base_sim, bridge_desc
        if (items[1] in ga or ga in items[1]) and (items[0] in gb or gb in items[0]):
            u_pair = 0.80
            return u_pair * bridge_score * base_sim, bridge_desc
    return 0.0, ""

def infer_track_acoustics(artist: str, title: str, mood_label: str = "") -> dict:
    """Вычисляет акустический профиль трека (BPM, Camelot, Mood, Genre) по имени и настроению."""
    artist = (artist or "").strip()
    title = (title or "").strip()
    mood_label = (mood_label or "").strip()
    seed_str = f"{artist.lower()}|{title.lower()}"
    h = int(hashlib.sha256(seed_str.encode("utf-8")).hexdigest()[:8], 16)

    bpm_pool = [72, 80, 85, 92, 100, 110, 118, 120, 124, 128, 132, 140]
    bpm = bpm_pool[h % len(bpm_pool)]

    cam_num = (h % 12) + 1
    cam_letter = "A" if ((h >> 4) % 3 != 0) else "B"
    camelot_key = f"{cam_num}{cam_letter}"

    valence = round(0.20 + (((h >> 8) % 60) / 100.0), 2)
    energy = round(0.30 + (((h >> 14) % 65) / 100.0), 2)

    art_l = artist.lower()
    tit_l = title.lower()
    mood_l = mood_label.lower()
    if any(w in art_l or w in mood_l for w in ["молчат дома", "ploho", "пост-панк", "post-punk", "дурной"]):
        genre = "post-punk"
        bpm = 140
        camelot_key = "9A"
        valence = 0.28
        energy = 0.74
    elif any(w in art_l or w in mood_l for w in ["sirotkin", "сироткин", "август", "теплый винил", "инди"]):
        genre = "indie"
        bpm = 118
        camelot_key = "8B"
        valence = 0.42
        energy = 0.54
    elif any(w in art_l or w in mood_l for w in ["pompeya", "дрим", "dream"]):
        genre = "dream-pop"
        bpm = 120
        camelot_key = "8A"
        valence = 0.65
        energy = 0.60
    elif any(w in art_l or w in mood_l for w in ["burial", "трип-хоп", "trip-hop", "lo-fi", "лоу-фай"]):
        genre = "trip-hop"
        bpm = 70
        camelot_key = "8A"
        valence = 0.30
        energy = 0.48
    elif any(w in art_l or w in mood_l for w in ["техно", "techno", "minimal"]):
        genre = "minimal-techno"
        bpm = 124
        camelot_key = "8A"
        valence = 0.35
        energy = 0.82
    elif any(w in art_l or w in mood_l for w in ["синт", "synth"]):
        genre = "synth-pop"
        bpm = 120
        camelot_key = "8A"
        valence = 0.50
        energy = 0.68
    else:
        genre = "indie"

    return {
        "bpm": bpm,
        "camelotKey": camelot_key,
        "valence": valence,
        "energy": energy,
        "genre": genre
    }

def calculate_full_resonance(track_a: dict, track_b: dict) -> tuple[int, str]:
    """Итоговый расчет резонанса по формуле ChatGPT: R_AB = 100 * [ B_AB + lambda*(1-B_AB)*S_AB ]."""
    k_ab = calculate_camelot_harmonic(track_a.get("camelotKey", "8A"), track_b.get("camelotKey", "8A"))
    t_ab = calculate_tempo_harmonic(float(track_a.get("bpm", 120)), float(track_b.get("bpm", 120)))
    w_ab = 0.84
    m_ab = calculate_mood_vector(
        float(track_a.get("valence", 0.4)), float(track_a.get("energy", 0.5)),
        float(track_b.get("valence", 0.4)), float(track_b.get("energy", 0.5))
    )
    e_ab = 0.86

    b_ab = (0.15 * k_ab) + (0.10 * t_ab) + (0.25 * w_ab) + (0.15 * m_ab) + (0.35 * e_ab)

    s_ab, bridge_desc = calculate_serendipity_engine(
        track_a.get("genre", "indie"), track_b.get("genre", "indie"), b_ab
    )

    lambda_factor = 0.25
    r_ab = 100.0 * (b_ab + lambda_factor * (1.0 - b_ab) * s_ab)
    final_compat = int(min(99, max(58, round(r_ab))))

    if bridge_desc:
        story = f"Культурный мост ({bridge_desc}): октавный резонанс {int(track_a.get('bpm', 120))}/{int(track_b.get('bpm', 120))} BPM."
    elif final_compat >= 88:
        story = f"Редкое созвучие Camelot ({track_a.get('camelotKey', '8A')} ↔ {track_b.get('camelotKey', '8A')}) и фазовый синхрон темпа."
    elif final_compat >= 75:
        story = "Тёплый вечерний резонанс: ваши звуковые спектры пересеклись в этой точке города."
    else:
        story = "Случайная встреча контрастов в ночном эфире."

    return final_compat, story

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


def generate_echo_token() -> str:
    """Новый случайный токен доступа к эфиру (32 hex-символа)."""
    return secrets.token_hex(16)


def load_config():
    cfg = None
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
        except Exception as e:
            logger.warning("Config load error: %s", e)
            cfg = None

    if not isinstance(cfg, dict):
        cfg = {"gemini_api_key": os.environ.get("GEMINI_API_KEY", "")}

    changed = False
    env_token = os.environ.get("ECHO_TOKEN", "").strip()
    if env_token:
        # Явное указание токена из окружения имеет приоритет (удобно для Docker/CI)
        if cfg.get("echo_token") != env_token:
            cfg["echo_token"] = env_token
            changed = True
    elif not cfg.get("echo_token"):
        cfg["echo_token"] = generate_echo_token()
        changed = True
        logger.info("🔑 Сгенерирован новый Echo Token (сохранён в echo_config.json)")

    if not cfg.get("gemini_api_key"):
        cfg["gemini_api_key"] = os.environ.get("GEMINI_API_KEY", "")

    if changed:
        save_config(cfg)
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
        if "min_resonance" not in cap_cols:
            try:
                cur.execute("ALTER TABLE capsules ADD COLUMN min_resonance INTEGER DEFAULT 0")
                cur.execute("ALTER TABLE capsules ADD COLUMN secret_perk TEXT DEFAULT ''")
            except Exception as e:
                logger.debug("ALTER TABLE capsules min_resonance/secret_perk: %s", e)
        conn.commit()

        # Гигиена эфира: убираем служебные записи браузеров/расширений,
        # которые могли попасть в базу через SMTC или старые версии клиентов.
        try:
            cur.execute("SELECT id, artist, title FROM devices")
            junk_ids = [row[0] for row in cur.fetchall() if is_junk_track(row[1], row[2])]
            for junk_id in junk_ids:
                cur.execute("DELETE FROM devices WHERE id = ?", (junk_id,))
            if junk_ids:
                conn.commit()
                logger.info("🧹 Эфир очищен от %d служебных записей", len(junk_ids))
        except Exception as e:
            logger.debug("junk cleanup error: %s", e)

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
                    52, 50.0, 0.36, 95, 1, c1_lat, c1_lon, 0, "", 0, "", now
                ),
                (
                    "cap_seed_02", "echo_curator", "Sirotkin", "Выше домов",
                    "Теплый винил", "Архивариус забытых кассет", "Вечерняя улица",
                    "Остановись на перекрестке и подними глаза к крышам домов.",
                    95, 215.0, 0.55, 91, 1, c2_lat, c2_lon, 0, "", 0, "", now
                ),
                (
                    "cap_venue_03", "venue_vinyl_lab", "Massive Attack", "Teardrop",
                    "Аналоговый трип-хоп", "Виниловая лавка «Фонограф»", "Сретенка · Камерные бары",
                    "Звуковой тайник «Фонографа»: мы на одной волне!",
                    70, 160.0, 0.42, 97, 1, c3_lat, c3_lon, 1, "Виниловая лавка «Фонограф»",
                    75, "Кодовое слово «ЭХО-33»: фильтр-кофе и скидка 15% на винил дня", now
                )
            ]
            cur.executemany("""
                INSERT INTO capsules (
                    id, creator_id, artist, title, mood_label, persona_title,
                    zone_name, secret_message, distance_meters, angle_degrees,
                    distance_ratio, compatibility_percent, is_unlocked, lat, lon,
                    is_venue, venue_name, min_resonance, secret_perk, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
    global GEMINI_WARNED
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

    # Актуальные модели Gemini (1.5-линейка отключена Google и отвечает 404).
    # Порядок: сначала основная, затем более дешёвые/быстрые резервы.
    models_to_try = [
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
    ]
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
            if not GEMINI_WARNED:
                GEMINI_WARNED = True
                logger.warning(
                    "Gemini недоступен (%s). Работаем на локальной библиотеке архетипов. "
                    "Частая причина: гео-ограничение Gemini для России или исчерпанная квота ключа.",
                    e
                )
            else:
                logger.debug("Gemini model %s failed: %s", model_name, e)
            continue
    return None


def generate_curated_persona(artist: str, title: str, device_id: str, incoming_recent_tracks: list = None):
    artist = sanitize_text(artist, MAX_TEXT_LEN) or "Неизвестный исполнитель"
    title = sanitize_text(title, MAX_TEXT_LEN) or "Без названия"
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
    if last and (last[0] or "").strip().lower() == artist.strip().lower() and (last[1] or "").strip().lower() == title.strip().lower():
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


def build_state_payload(for_device_id: str = None, include_token: bool = False):
    with DB_LOCK:
        conn = get_db_connection()
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()

        now_sec = time.time()

        # Автоматическая очистка старых треков старше 90 минут (кроме постоянных маяков)
        cutoff_time = now_sec - (90 * 60)
        cur.execute("DELETE FROM devices WHERE updated_at < ? AND id NOT LIKE 'sig_%'", (cutoff_time,))
        # Золотой фонд ЭХО (sig_*) — постоянные маяки демо-города: всегда «живые»
        cur.execute("UPDATE devices SET updated_at = ? WHERE id LIKE 'sig_%' AND updated_at < ?",
                    (now_sec, now_sec - 240))
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

        for r_item in dev_rows:
            if for_device_id and r_item["id"] == for_device_id:
                my_device_record = r_item
                break

        my_acoustics = infer_track_acoustics(
            my_device_record["artist"], my_device_record["title"], my_device_record["mood_label"]
        ) if my_device_record else None

        for row in dev_rows:
            dev_id = row["id"]
            if for_device_id and dev_id == for_device_id:
                continue

            try:
                reactions = json.loads(row["reactions_json"] or "{}")
            except Exception as e:
                logger.debug("reactions_json parse error for %s: %s", row["id"], e)
                reactions = {}

            updated_at = float(row["updated_at"] or 0)
            age_min = max(0, int((now_sec - updated_at) / 60.0))
            is_ghost = age_min > 5

            other_acoustics = infer_track_acoustics(row["artist"], row["title"], row["mood_label"])
            if my_acoustics:
                compat_num, res_story = calculate_full_resonance(my_acoustics, other_acoustics)
            else:
                compat_num = int(row["compatibility_percent"] or 88)
                res_story = "Тёплый вечерний резонанс: ваши звуковые траектории пересеклись в этой точке города."

            encounters_num = int(row["encounters_count"] or 1)
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
                "compatibilityPercent": compat_num,
                "bpm": other_acoustics["bpm"],
                "camelotKey": other_acoustics["camelotKey"],
                "genre": other_acoustics["genre"],
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
            if dev_id in DEVICE_COVERS:
                sig["coverUrl"] = f"/api/cover/{dev_id}"
            signals.append(sig)
            zone_buckets.setdefault(sig["zoneName"], []).append(sig)

        cur.execute("SELECT * FROM capsules ORDER BY created_at DESC LIMIT 30")
        cap_rows = cur.fetchall()
        capsules = []
        for crow in cap_rows:
            is_ven = bool(crow["is_venue"]) if "is_venue" in crow.keys() else False
            ven_name = crow["venue_name"] if "venue_name" in crow.keys() else ""
            min_res = int(crow["min_resonance"]) if "min_resonance" in crow.keys() and crow["min_resonance"] is not None else 0
            perk = crow["secret_perk"] if "secret_perk" in crow.keys() and crow["secret_perk"] is not None else ""
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
                "minResonance": min_res,
                "secretPerk": perk,
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
            if now_sec - r["time"] < 60.0:
                t_id = r.get("targetId", "")
                s_id = r.get("senderId", "")
                if for_device_id:
                    # Do not show own reaction to self
                    if s_id != for_device_id:
                        # Match if targeted to this device, or broadcast/all, or to any beacon/node in the zone
                        if t_id == for_device_id or t_id in ("broadcast", "all", "", None) or t_id.startswith("sig_") or t_id.startswith("cap_") or t_id == "pc_studio_node":
                            active_banner = {
                                "id": r["id"],
                                "targetId": t_id,
                                "senderId": s_id,
                                "symbol": r["symbol"],
                                "message": r["message"],
                                "time": r["time"]
                            }
                            break
                else:
                    if s_id != "dashboard":
                        active_banner = {
                            "id": r["id"],
                            "targetId": t_id,
                            "senderId": s_id,
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

    reactions_history = []
    with REACTIONS_LOCK:
        for r in RECENT_REACTIONS:
            target = str(r.get("targetId", "") or "")
            # Пульт и «публичные» отклики видны всем; личные отклики — только участникам
            visible = (
                not for_device_id
                or for_device_id == "dashboard"
                or target in (for_device_id, "", "broadcast", "all")
                or target.startswith(("sig_", "cap_", "pc_studio_node"))
                or r.get("senderId") == for_device_id
            )
            if visible:
                reactions_history.append({
                    "id": r["id"],
                    "targetId": r.get("targetId", ""),
                    "senderId": r.get("senderId", ""),
                    "symbol": r.get("symbol", "✦"),
                    "message": r.get("message", "Спасибо за созвучие"),
                    "time": r.get("time", now_sec)
                })

    # 6. АРГОН: Точка Лагранжа (Акустическое затмение при 3+ созвучных слушателях в квартале)
    lagrange_point = None
    eligible_for_lagrange = [s for s in signals if not s.get("isGhost") and s.get("compatibilityPercent", 0) >= 75]
    if len(eligible_for_lagrange) >= 3:
        triplet = eligible_for_lagrange[:3]
        avg_ang = sum(t["angleDegrees"] for t in triplet) / 3.0
        avg_rat = sum(t["distanceRatio"] for t in triplet) / 3.0
        lagrange_point = {
            "active": True,
            "angleDegrees": round(avg_ang, 1),
            "distanceRatio": round(avg_rat, 2),
            "secretTrack": "Burial — Archangel (Nocturnal Re-edit)",
            "secretMessage": "Акустическое затмение: три ночных слушателя образовали Точку Лагранжа.",
            "participants": [t["id"] for t in triplet]
        }

    # 7. АРГОН: Активные тепловые пятна (Призрачное тепло / дыхание на стекле)
    active_thermal_patches = []
    for p in list(THERMAL_PATCHES):
        age = now_sec - p["time"]
        if age <= 15.0:
            if not for_device_id or p["targetId"] in (for_device_id, "") or p["senderId"] == for_device_id:
                active_thermal_patches.append({
                    "id": p["id"],
                    "targetId": p["targetId"],
                    "senderId": p["senderId"],
                    "x": p["x"],
                    "y": p["y"],
                    "ageSec": round(age, 1)
                })

    THERMAL_PATCHES[:] = [p for p in THERMAL_PATCHES if (now_sec - p["time"]) <= 15.0]

    # 8. АРГОН: Синхронизированные иглы (Drop-in синхрон)
    needle_synced_targets = []
    for s_id, s_info in list(NEEDLE_SYNCS.items()):
        if now_sec - s_info["time"] <= 60.0:
            if s_id == for_device_id:
                needle_synced_targets.append(s_info["targetId"])
            elif s_info["targetId"] == for_device_id:
                needle_synced_targets.append(s_id)

    # 9. АРГОН: Шум рассвета (Dawn Noise) с 4:00 до 6:00
    now_hour = datetime.now().hour
    is_dawn_mode = (4 <= now_hour < 6)

    server_meta = {
        "version": SERVER_VERSION,
        "startedAt": int(SERVER_STARTED_AT),
        "uptimeSec": int(now_sec - SERVER_STARTED_AT),
        "hasAi": bool(str(SERVER_CONFIG.get("gemini_api_key", "")).strip()),
        "aiError": bool(GEMINI_WARNED),
        "python": sys.version.split()[0],
        "publicTunnelHost": SERVER_CONFIG.get("public_tunnel_host", ""),
        "tokenRequired": True,
        "listenersOnline": len([s for s in signals if not s.get("isGhost")])
    }
    if include_token:
        server_meta["token"] = SERVER_CONFIG.get("echo_token", "")

    return {
        "status": "ok",
        "serverTime": int(now_sec),
        "serverMeta": server_meta,
        "myDeviceId": for_device_id or "",
        "signals": signals,
        "capsules": capsules,
        "eveningTraces": evening_traces,
        "cityZones": city_zones,
        "incomingBanner": active_banner,
        "reactionsHistory": reactions_history[-25:],
        "myReactions": my_reactions,
        "myRefinedMood": my_refined_mood,
        "myCuratedPersona": my_curated_persona,
        "myCuratedObservation": my_curated_obs,
        "myColorHex": my_color_hex,
        "lagrangePoint": lagrange_point,
        "thermalPatches": active_thermal_patches,
        "needleSyncedTargets": needle_synced_targets,
        "isDawnMode": is_dawn_mode
    }


def build_evening_playlist(limit: int = 25):
    """Плейлист вечера: уникальные треки из истории прослушиваний с ссылками на плееры."""
    limit = max(1, min(int(limit or 25), 60))
    try:
        with DB_LOCK:
            conn = get_db_connection()
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                "SELECT artist, title, persona_title, zone_name, played_at FROM listening_history "
                "ORDER BY id DESC LIMIT 200"
            )
            rows = cur.fetchall()
            conn.close()
    except Exception as e:
        logger.debug("playlist query error: %s", e)
        rows = []

    seen = set()
    tracks = []
    for row in rows:
        artist = sanitize_text(row["artist"], MAX_TEXT_LEN)
        title = sanitize_text(row["title"], MAX_TEXT_LEN)
        if is_junk_track(artist, title):
            continue
        key = (artist.lower(), title.lower())
        if key in seen:
            continue
        seen.add(key)
        query = urllib.parse.quote(f"{artist} {title}".strip())
        tracks.append({
            "artist": artist,
            "title": title,
            "personaTitle": sanitize_text(row["persona_title"], 80) or "Ночной слушатель",
            "zoneName": sanitize_text(row["zone_name"], 80) or "Вечерняя улица",
            "playedAt": sanitize_text(row["played_at"], 16),
            "yandexUrl": f"https://music.yandex.ru/search?text={query}",
            "appleUrl": f"https://music.apple.com/ru/search?term={query}",
            "vkUrl": f"https://vk.com/audio?q={query}"
        })
        if len(tracks) >= limit:
            break

    if tracks:
        lines = [
            f"{i + 1}. {t['artist']} — {t['title']} ({t['personaTitle']})"
            for i, t in enumerate(tracks)
        ]
        text = "✦ Плейлист моего вечера • ЭХО ✦\n\n" + "\n".join(lines)
    else:
        text = "✦ Плейлист моего вечера • ЭХО ✦\n\nВ эфире пока нет треков."

    return {
        "status": "ok",
        "title": "Плейлист вечера",
        "generatedAt": int(time.time()),
        "count": len(tracks),
        "tracks": tracks,
        "text": text
    }


_TEMPLATE_CACHE = {}
_TEMPLATE_LOCK = threading.Lock()


def _load_template(name: str) -> str:
    """Читает шаблон с кэшем по mtime: правки в templates/ подхватываются без рестарта."""
    tpl_path = os.path.join(BASE_DIR, "templates", name)
    try:
        mtime = os.path.getmtime(tpl_path)
    except OSError as e:
        logger.warning("Template %s not found: %s", name, e)
        return f"<h1>Template {name} not found</h1>"

    with _TEMPLATE_LOCK:
        cached = _TEMPLATE_CACHE.get(name)
        if cached and cached[0] == mtime:
            return cached[1]

    try:
        with open(tpl_path, "r", encoding="utf-8") as f:
            text = f.read()
    except Exception as e:
        logger.warning("Template %s read error: %s", name, e)
        return f"<h1>Template {name} not found</h1>"

    with _TEMPLATE_LOCK:
        _TEMPLATE_CACHE[name] = (mtime, text)
    return text


# Прогреваем кэш при старте (и падаем в понятный текст, если шаблона нет)
DASHBOARD_HTML = _load_template("dashboard.html")
PWA_HTML = _load_template("pwa.html")





class EchoRequestHandler(BaseHTTPRequestHandler):
    server_version = "EchoRadar/" + SERVER_VERSION
    sys_version = ""

    def log_message(self, format, *args):
        # Собственный логгер ниже; стандартный HTTP-лог отключён, чтобы не шуметь.
        return

    # --- Определение клиента (прямой LAN или публичный туннель) ---

    def _client_ip(self) -> str:
        forwarded = (self.headers.get("CF-Connecting-IP") or "").strip()
        if not forwarded:
            xff = (self.headers.get("X-Forwarded-For") or "").strip()
            forwarded = xff.split(",")[0].strip() if xff else ""
        if forwarded:
            try:
                return str(ipaddress.ip_address(forwarded))
            except ValueError:
                pass
        return str(getattr(self, "client_address", ("",))[0] or "")

    def _is_proxied(self) -> bool:
        """Запрос пришёл через туннель/прокси (Cloudflare, ngrok, обратный прокси)."""
        return bool(
            self.headers.get("CF-Connecting-IP")
            or self.headers.get("CF-Ray")
            or self.headers.get("X-Forwarded-For")
            or self.headers.get("X-Real-IP")
            or self.headers.get("Forwarded")
        )

    def _is_local_client(self) -> bool:
        """Прямой клиент домашней сети. Туннельный трафик локальным НЕ считается,
        даже если cloudflared подключается с 127.0.0.1."""
        if self._is_proxied():
            return False
        ip = self._client_ip()
        if ip in ("", "localhost"):
            return True
        try:
            addr = ipaddress.ip_address(ip)
        except ValueError:
            return False
        return bool(addr.is_loopback or addr.is_private)

    # --- Ответы ---

    def _base_headers(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Permissions-Policy", "geolocation=(self), camera=(), microphone=()")
        self.send_header("X-Robots-Tag", "noindex, nofollow")

    def _same_origin(self) -> str:
        """CORS только для своего же origin: пульт/сонар не должны читаться чужими сайтами."""
        origin = (self.headers.get("Origin") or "").strip()
        if not origin:
            return ""
        try:
            origin_host = urllib.parse.urlparse(origin).netloc.lower()
        except Exception:
            return ""
        host = (self.headers.get("Host") or "").strip().lower()
        return origin if origin_host and origin_host == host else ""

    def _send_cors(self):
        allowed = self._same_origin()
        if allowed:
            self.send_header("Access-Control-Allow-Origin", allowed)
            self.send_header("Vary", "Origin")

    def _send_json(self, payload, status=200):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self._send_cors()
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept, X-Echo-Token, X-Device-Id")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self._base_headers()
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _send_html(self, html_text, status=200):
        raw = html_text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        csp = (
            "default-src 'self'; "
            "script-src 'self' 'unsafe-inline' https://unpkg.com; "
            "style-src 'self' 'unsafe-inline' https://unpkg.com; "
            "img-src 'self' data: blob: https:; "
            "connect-src 'self'; "
            "font-src 'self' data:; "
            "object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
        )
        self.send_header("Content-Security-Policy", csp)
        token = str(SERVER_CONFIG.get("echo_token", "") or "")
        if token:
            cookie = f"echo_token={token}; Path=/; SameSite=Lax; HttpOnly; Max-Age=31536000"
            if self.headers.get("X-Forwarded-Proto", "").lower() == "https":
                cookie += "; Secure"
            self.send_header("Set-Cookie", cookie)
        self._base_headers()
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    # --- Авторизация ---

    def _cookie_token(self) -> str:
        cookie_header = self.headers.get("Cookie", "")
        for part in cookie_header.split(";"):
            name, _, value = part.strip().partition("=")
            if name == "echo_token":
                return value.strip()
        return ""

    def _check_token(self) -> bool:
        """Проверяет токен. Прямые клиенты LAN/loopback проходят без токена,
        а весь трафик через публичный туннель обязан предъявить X-Echo-Token,
        cookie echo_token или ?token=..."""
        if self._is_local_client():
            return True

        expected = str(SERVER_CONFIG.get("echo_token", "") or "").strip()
        if not expected:
            return True

        candidates = [
            (self.headers.get("X-Echo-Token") or "").strip(),
            (self.headers.get("X-API-Key") or "").strip(),
            self._cookie_token(),
        ]
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        candidates.append((query.get("token", [""])[0] or "").strip())

        return any(c and hmac.compare_digest(c, expected) for c in candidates)

    def do_OPTIONS(self):
        self.send_response(204)
        self._send_cors()
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept, X-Echo-Token, X-Device-Id")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        client_ip = self._client_ip()

        if path == "/api/health":
            self._send_json({
                "status": "ok",
                "version": SERVER_VERSION,
                "uptimeSec": int(time.time() - SERVER_STARTED_AT),
                "hasAi": bool(str(SERVER_CONFIG.get("gemini_api_key", "")).strip())
            })
            return

        if path == "/favicon.ico":
            self.send_response(204)
            self._base_headers()
            self.end_headers()
            return

        if path.startswith("/api/cover/"):
            target_id = path[len("/api/cover/"):]
            if not rate_limit_allow(client_ip, STATE_RATE_LIMIT):
                self._send_json({"status": "error", "message": "Too many requests"}, status=429)
                return
            if target_id in DEVICE_COVERS:
                mime_type, cover_raw = DEVICE_COVERS[target_id]
                self.send_response(200)
                self.send_header("Content-Type", mime_type)
                self._send_cors()
                self.send_header("Cache-Control", "public, max-age=3600")
                self._base_headers()
                self.send_header("Content-Length", str(len(cover_raw)))
                self.end_headers()
                self.wfile.write(cover_raw)
            else:
                self.send_response(404)
                self._base_headers()
                self.end_headers()
            return

        if path == "/api/state":
            if not rate_limit_allow(client_ip, STATE_RATE_LIMIT):
                self._send_json({"status": "error", "message": "Too many requests"}, status=429)
                return
            if not self._check_token():
                self._send_json({"status": "error", "message": "Unauthorized", "authRequired": True}, status=401)
                return
            query = urllib.parse.parse_qs(parsed.query)
            dev_id = query.get("deviceId", [None])[0] or self.headers.get("X-Device-Id")
            dev_id = sanitize_text(dev_id, 80) if dev_id else None
            self._send_json(build_state_payload(for_device_id=dev_id, include_token=self._is_local_client()))
            return

        if path == "/api/playlist":
            if not rate_limit_allow(client_ip, STATE_RATE_LIMIT):
                self._send_json({"status": "error", "message": "Too many requests"}, status=429)
                return
            if not self._check_token():
                self._send_json({"status": "error", "message": "Unauthorized", "authRequired": True}, status=401)
                return
            query = urllib.parse.parse_qs(parsed.query)
            try:
                limit = int(query.get("limit", ["25"])[0])
            except (TypeError, ValueError):
                limit = 25
            self._send_json(build_evening_playlist(limit=limit))
            return

        if path in ("/", "/index.html"):
            self._send_html(_load_template("dashboard.html"))
            return
        if path in ("/app", "/pwa"):
            self._send_html(_load_template("pwa.html"))
            return

        self._send_json({"status": "error", "message": "Not found", "endpoint": path}, status=404)

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        client_ip = self._client_ip()

        # Защищённые эндпоинты требуют токен
        PROTECTED_PATHS = {
            "/api/signal", "/api/react", "/api/drop_capsule", "/api/delete_signal",
            "/api/delete_capsule", "/api/save_config", "/api/bump_encounter",
            "/api/needle_sync", "/api/send_warmth", "/api/wave_impulse",
        }
        if path in PROTECTED_PATHS and not self._check_token():
            self._send_json({"status": "error", "message": "Unauthorized", "authRequired": True}, status=401)
            return

        if not rate_limit_allow(client_ip, WRITE_RATE_LIMIT):
            self._send_json({"status": "error", "message": "Too many requests"}, status=429)
            return

        try:
            length = int(self.headers.get("Content-Length", 0))
        except (TypeError, ValueError):
            length = 0
        if length > MAX_BODY_BYTES:
            self._send_json({"status": "error", "message": "Payload too large"}, status=413)
            return
        body_raw = self.rfile.read(length).decode("utf-8", errors="ignore") if length > 0 else "{}"
        try:
            data = json.loads(body_raw)
        except Exception as e:
            logger.debug("do_POST body parse error: %s", e)
            data = {}

        if path == "/api/signal":
            dev_id = sanitize_text(data.get("id") or f"dev_{int(time.time())}", 80)
            artist = sanitize_text(data.get("artist"), MAX_TEXT_LEN)
            title = sanitize_text(data.get("title"), MAX_TEXT_LEN)
            source_name = sanitize_text(data.get("sourceName") or "Android ЭХО", 60)
            zone_name = normalize_zone(data.get("zoneName"))

            # Служебные вкладки браузеров и пустые названия не должны попадать в эфир
            if is_junk_track(artist, title):
                logger.info("🚫 Служебный медиа-поток отклонён: %r — %r", artist, title)
                self._send_json({"status": "ignored", "reason": "junk_track", "artist": artist, "title": title})
                return

            cover_b64 = data.get("coverBase64")
            if cover_b64:
                try:
                    c_bytes = base64.b64decode(cover_b64)
                    if 0 < len(c_bytes) <= MAX_COVER_BYTES:
                        c_mime = "image/png" if c_bytes.startswith(b"\x89PNG") else "image/jpeg"
                        DEVICE_COVERS[dev_id] = (c_mime, c_bytes)
                    else:
                        logger.debug("coverBase64 слишком большой (%d байт), пропущен", len(c_bytes))
                except Exception as e:
                    logger.debug("coverBase64 decode error: %s", e)

            incoming_recent = data.get("recentTracks", [])
            future = _PERSONA_EXECUTOR.submit(generate_curated_persona, artist, title, dev_id, incoming_recent)
            try:
                curated = future.result(timeout=5.0)
            except Exception as e:
                logger.warning("persona future timed out: %s", e)
                curated = {
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
            k = sanitize_text(data.get("gemini_api_key", ""), 200)
            SERVER_CONFIG["gemini_api_key"] = k
            save_config(SERVER_CONFIG)
            logger.info("⚙️ Ключ ИИ-куратора сохранен (%s).", 'Активен' if k else 'Сброшен')
            self._send_json({"status": "ok", "has_key": bool(k)})

        elif path == "/api/react":
            target_id = sanitize_text(data.get("targetId") or data.get("signalId") or "", 80)
            sender_id = sanitize_text(data.get("senderId") or "unknown_sender", 80)
            symbol = sanitize_text(data.get("symbol") or "✦", 8)
            custom_msg = sanitize_text(data.get("message") or "", MAX_MESSAGE_LEN)
            rec_id = next_event_id()
            msg = custom_msg if custom_msg else f"Спасибо за созвучие {symbol}"

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
            cap_id = f"cap_{next_event_id()}"
            creator_id = sanitize_text(data.get("creatorId") or "local_user", 80)
            artist = sanitize_text(data.get("artist") or "Sirotkin", MAX_TEXT_LEN)
            title = sanitize_text(data.get("title") or "Август", MAX_TEXT_LEN)
            zone_name = normalize_zone(data.get("zoneName"))
            secret_msg = sanitize_text(data.get("secretMessage") or "Слушай этот момент здесь...", MAX_MESSAGE_LEN)
            is_venue = 1 if data.get("isVenue") else 0
            venue_name = sanitize_text(data.get("venueName") or "", 80)
            min_resonance = max(0, min(100, int(data.get("minResonance") or (75 if is_venue else 0))))
            secret_perk = sanitize_text(data.get("secretPerk") or "", 200)

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
                        is_venue, venue_name, min_resonance, secret_perk, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    cap_id, creator_id, artist, title, mood_label, persona_title,
                    zone_name, secret_msg, dist_meters, angle_deg,
                    dist_ratio, curated["compatibilityPercent"], lat, lon,
                    is_venue, venue_name, min_resonance, secret_perk, time.time()
                ))
                conn.commit()
                conn.close()

            log_tag = f"🏛️ ТАЙНИК ЛОКАЦИИ [{venue_name}]" if is_venue else "⏳ ТАЙНИК"
            logger.info("%s [%s] %s — %s: «%s»", log_tag, zone_name, artist, title, secret_msg)
            self._send_json(build_state_payload(for_device_id=creator_id))

        elif path == "/api/bump_encounter":
            target_id = sanitize_text(data.get("targetId") or "", 80)
            sender_id = sanitize_text(data.get("senderId") or "", 80)
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

        elif path == "/api/needle_sync":
            target_id = sanitize_text(data.get("targetId") or "", 80)
            sender_id = sanitize_text(data.get("senderId") or "anon", 80)
            now_ts = time.time()
            NEEDLE_SYNCS[sender_id] = {"targetId": target_id, "time": now_ts}
            with REACTIONS_LOCK:
                RECENT_REACTIONS.append({
                    "id": next_event_id(),
                    "targetId": target_id,
                    "senderId": sender_id,
                    "symbol": "∿",
                    "message": "Кто-то встал рядом и слушает с тобой (Синхронизация иглы)",
                    "time": now_ts
                })
            logger.info("🪡 СИНХРОНИЗАЦИЯ ИГЛЫ: %s -> %s", sender_id, target_id)
            self._send_json({"status": "ok", "synced": True, "targetId": target_id})

        elif path == "/api/send_warmth":
            target_id = sanitize_text(data.get("targetId") or "", 80)
            sender_id = sanitize_text(data.get("senderId") or "anon", 80)
            try:
                patch_x = max(0.0, min(1.0, float(data.get("x", 0.5))))
                patch_y = max(0.0, min(1.0, float(data.get("y", 0.5))))
            except (TypeError, ValueError):
                patch_x, patch_y = 0.5, 0.5
            p_id = f"warmth_{next_event_id()}"
            THERMAL_PATCHES.append({
                "id": p_id,
                "targetId": target_id,
                "senderId": sender_id,
                "x": patch_x,
                "y": patch_y,
                "time": time.time()
            })
            # Не копим бесконечную историю тепловых пятен
            if len(THERMAL_PATCHES) > 40:
                del THERMAL_PATCHES[:-40]
            logger.info("♨️ ТЕПЛОВОЙ СЛЕД от %s в точке (%.2f, %.2f)", sender_id, patch_x, patch_y)
            self._send_json({"status": "ok", "patchId": p_id})

        elif path == "/api/wave_impulse":
            target_id = sanitize_text(data.get("targetId") or "", 80)
            sender_id = sanitize_text(data.get("senderId") or "anon", 80)
            now_ts = time.time()
            with REACTIONS_LOCK:
                RECENT_REACTIONS.append({
                    "id": next_event_id(),
                    "targetId": target_id,
                    "senderId": sender_id,
                    "symbol": "🌊",
                    "message": "Звуковой импульс сквозь ночной эфир (Камень в воду)",
                    "time": now_ts
                })
                if len(RECENT_REACTIONS) > 30:
                    del RECENT_REACTIONS[:-30]
            logger.info("🌊 ИМПУЛЬС ВОЛНЫ от %s -> %s", sender_id, target_id)
            self._send_json({"status": "ok"})

        elif path == "/api/delete_signal":
            target_id = sanitize_text(data.get("targetId") or data.get("id") or "", 80)
            with DB_LOCK:
                conn = get_db_connection()
                cur = conn.cursor()
                cur.execute("DELETE FROM devices WHERE id = ?", (target_id,))
                conn.commit()
                conn.close()
            logger.info("🗑️ СИГНАЛ %s удален из эфира", target_id)
            self._send_json(build_state_payload())

        elif path == "/api/delete_capsule":
            target_id = sanitize_text(data.get("targetId") or data.get("id") or "", 80)
            with DB_LOCK:
                conn = get_db_connection()
                cur = conn.cursor()
                cur.execute("DELETE FROM capsules WHERE id = ?", (target_id,))
                deleted = cur.rowcount or 0
                conn.commit()
                conn.close()
            logger.info("🗑️ ТАЙНИК %s удален (строк: %d)", target_id, deleted)
            self._send_json({"status": "ok", "deleted": deleted, "state": build_state_payload()})

        else:
            self._send_json({"status": "error", "message": "Unknown endpoint", "endpoint": path}, status=404)


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

                # Служебные вкладки браузеров и пустые названия не попадают в эфир
                if is_junk_track(artist, title):
                    if (artist, title) != last_logged:
                        last_logged = (artist, title)
                        logger.info("🚫 SMTC: служебный поток проигнорирован — %r / %r", artist, title)
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
    daemon_threads = True
    allow_reuse_address = True

    def handle_error(self, request, client_address):
        # Подавляем шумные разрывы соединений от мобильных клиентов (WinError 10054 / ConnectionResetError)
        ex_type, ex_val, _ = sys.exc_info()
        if ex_type in (ConnectionResetError, BrokenPipeError) or (isinstance(ex_val, OSError) and getattr(ex_val, "winerror", None) == 10054):
            return
        super().handle_error(request, client_address)

def main():
    init_db()
    start_udp_broadcast()
    if os.environ.get("ECHO_DISABLE_SMTC", "").strip() not in ("1", "true", "yes"):
        start_windows_media_listener()
    local_ip = get_local_ip()
    echo_token = SERVER_CONFIG.get("echo_token", "")
    server = EchoServer(("0.0.0.0", HTTP_PORT), EchoRequestHandler)
    logger.info("=" * 66)
    logger.info(" 📡 ЭХО (Echo Radar) v%s — Ночной акустический атлас запущен!", SERVER_VERSION)
    logger.info(" 🌐 Локальный адрес в сети : http://%s:%d", local_ip, HTTP_PORT)
    logger.info(" 📱 Сенсорный Сонар и Карта : http://%s:%d/app", local_ip, HTTP_PORT)
    logger.info(" ⚙️  Пульт управления и ИИ  : http://%s:%d/", local_ip, HTTP_PORT)
    logger.info(" 🩺 Проверка состояния       : http://%s:%d/api/health", local_ip, HTTP_PORT)
    if echo_token:
        logger.info(" 🔑 Echo Token (для Android) : %s", echo_token)
    logger.info(" 🔒 Туннель требует токен; локальная сеть — без токена")
    logger.info("=" * 66)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("\nСервер ЭХО остановлен.")
        server.server_close()


if __name__ == "__main__":
    main()
