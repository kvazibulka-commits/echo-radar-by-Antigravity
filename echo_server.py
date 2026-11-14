import math
import sqlite3
import time
from typing import List, Optional
from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(title="ЭХО Radar // Industrial Audio Presence Server", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

DB_PATH = "echo_industrial.db"

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    with get_db() as conn:
        conn.executescript("""
        CREATE TABLE IF NOT EXISTS signals (
            device_token TEXT PRIMARY KEY,
            artist TEXT NOT NULL,
            track TEXT NOT NULL,
            album TEXT,
            source TEXT,
            lat REAL,
            lon REAL,
            bpm INTEGER DEFAULT 120,
            camelot TEXT DEFAULT '8A',
            genre TEXT DEFAULT 'Инди',
            is_playing INTEGER DEFAULT 1,
            updated_at INTEGER
        );

        CREATE TABLE IF NOT EXISTS venue_beacons (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            venue_type TEXT DEFAULT 'COFFEE',
            current_track TEXT NOT NULL,
            current_artist TEXT NOT NULL,
            streaming_source TEXT DEFAULT 'Звук',
            min_resonance INTEGER DEFAULT 75,
            proof_code TEXT NOT NULL,
            discount_description TEXT NOT NULL,
            lat REAL,
            lon REAL,
            address TEXT
        );

        CREATE TABLE IF NOT EXISTS time_capsules (
            id TEXT PRIMARY KEY,
            artist TEXT NOT NULL,
            track TEXT NOT NULL,
            message TEXT NOT NULL,
            author_label TEXT DEFAULT 'Анонимный след',
            lat REAL,
            lon REAL,
            location_name TEXT,
            created_at INTEGER,
            expires_at INTEGER
        );

        CREATE TABLE IF NOT EXISTS pulses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender_token TEXT,
            target_signal_id TEXT,
            pulse_type TEXT,
            created_at INTEGER
        );
        """)

        # Seed initial beacons if empty
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM venue_beacons")
        if cur.fetchone()[0] == 0:
            cur.executemany("""
            INSERT INTO venue_beacons (id, name, venue_type, current_track, current_artist, streaming_source, min_resonance, proof_code, discount_description, lat, lon, address)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, [
                ("venue", "Кофейня «Соль»", "COFFEE", "Судно (Борис Рижий)", "Молчат Дома", "Яндекс Музыка", 75, "СУДНО", "Фильтр-кофе −20% за созвучие", 55.7610, 37.6415, "Большая Никитская, 14"),
                ("book", "Дом книги на Сретенке", "BOOKSHOP", "Август", "Sirotkin", "Яндекс Музыка", 60, "АВГУСТ", "Закладка ручной работы внутри", 55.7630, 37.6380, "Сретенка, 8")
            ])

        now = int(time.time())
        cur.execute("SELECT COUNT(*) FROM signals")
        if cur.fetchone()[0] == 0:
            cur.executemany("""
            INSERT INTO signals (device_token, artist, track, album, source, lat, lon, bpm, camelot, genre, is_playing, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, [
                ("peer_s1", "Молчат Дома", "Судно (Борис Рижий)", "Этажи", "Яндекс Музыка", 55.7620, 37.6430, 128, "8A", "Пост-панк", 1, now + 86400 * 30),
                ("peer_s2", "Sirotkin", "Август", "Эхолот", "Яндекс Музыка", 55.7605, 37.6390, 92, "11B", "Инди-фолк", 1, now + 86400 * 30),
                ("peer_s3", "Burial", "Archangel", "Untrue", "Spotify", 55.7590, 37.6420, 138, "2A", "Future garage", 1, now + 86400 * 30),
                ("peer_s4", "КИНО", "Звезда по имени Солнце", "Звезда по имени Солнце", "VK Музыка", 55.7635, 37.6410, 118, "9A", "Русский рок", 1, now + 86400 * 30),
                ("peer_s5", "Beach House", "Space Song", "Depression Cherry", "Яндекс Музыка", 55.7580, 37.6370, 147, "5B", "Dream pop", 1, now + 86400 * 30),
                ("peer_s6", "Nils Frahm", "Says", "Spaces", "Звук", 55.7640, 37.6440, 68, "4A", "Неоклассика", 1, now + 86400 * 30),
                ("peer_s7", "Pompeya", "90", "Tropical", "Яндекс Музыка", 55.7600, 37.6450, 110, "7B", "Dream groove", 1, now + 86400 * 30)
            ])

        cur.execute("SELECT COUNT(*) FROM time_capsules")
        if cur.fetchone()[0] == 0:
            cur.execute("""
            INSERT INTO time_capsules (id, artist, track, message, author_label, lat, lon, location_name, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                "cap-1", "Дельфин", "Июнь",
                "Здесь в ноябре первый снег ложился на мокрый асфальт, и этот трек играл на повторе...",
                "Сквер у Патриарших", 55.7640, 37.5920, "Патриаршие пруды",
                int(time.time()), int(time.time()) + 7 * 86400
            ))
            conn.commit()

init_db()

# --- Schemas ---

class SignalIn(BaseModel):
    artist: str
    track: str
    album: Optional[str] = ""
    source: Optional[str] = "Яндекс Музыка"
    latitude: float
    longitude: float
    bpm: Optional[int] = 120
    camelot: Optional[str] = "8A"
    genre: Optional[str] = "Инди"
    isPlaying: Optional[bool] = True

class CapsuleIn(BaseModel):
    artist: str
    track: str
    message: str
    latitude: float
    longitude: float
    locationName: Optional[str] = "Точка в городе"

class PulseIn(BaseModel):
    targetSignalId: str
    pulseType: Optional[str] = "THANKS_FOR_MUSIC"

def haversine_distance(lat1, lon1, lat2, lon2) -> int:
    R = 6371000  # meters
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return int(R * c)

# --- Endpoints ---

@app.get("/api/health")
def health():
    return {"status": "ok", "service": "Echo Industrial Server", "time": int(time.time())}

@app.post("/api/signal")
def post_signal(data: SignalIn, x_echo_token: Optional[str] = Header(None)):
    token = x_echo_token or "anonymous"
    now = int(time.time())

    with get_db() as conn:
        conn.execute("""
        INSERT INTO signals (device_token, artist, track, album, source, lat, lon, bpm, camelot, genre, is_playing, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(device_token) DO UPDATE SET
            artist=excluded.artist,
            track=excluded.track,
            album=excluded.album,
            source=excluded.source,
            lat=excluded.lat,
            lon=excluded.lon,
            bpm=excluded.bpm,
            camelot=excluded.camelot,
            genre=excluded.genre,
            is_playing=excluded.is_playing,
            updated_at=excluded.updated_at
        """, (
            token, data.artist, data.track, data.album, data.source,
            data.latitude, data.longitude, data.bpm, data.camelot, data.genre,
            1 if data.isPlaying else 0, now
        ))
        conn.commit()
    return {"status": "ok"}

@app.get("/api/state")
def get_state(
    lat: float = Query(55.7558),
    lon: float = Query(37.6173),
    radius_meters: int = Query(1000),
    x_echo_token: Optional[str] = Header(None)
):
    token = x_echo_token or ""
    threshold = int(time.time()) - 1800  # active within last 30 minutes

    with get_db() as conn:
        # Nearby signals
        cur = conn.cursor()
        cur.execute("""
        SELECT device_token, artist, track, album, source, lat, lon, bpm, camelot, genre, is_playing, updated_at
        FROM signals
        WHERE updated_at >= ? AND device_token != ?
        """, (threshold, token))
        rows = cur.fetchall()

        signals = []
        for r in rows:
            dist = haversine_distance(lat, lon, r["lat"], r["lon"])
            if dist <= radius_meters:
                # Calculate polar angle relative to user
                dlon = r["lon"] - lon
                dlat = r["lat"] - lat
                angle = math.degrees(math.atan2(dlon, dlat)) % 360

                signals.append({
                    "id": r["device_token"],
                    "artist": r["artist"],
                    "track": r["track"],
                    "album": r["album"] or "",
                    "source": r["source"] or "Яндекс Музыка",
                    "distanceMeters": max(15, dist),
                    "angleDegrees": round(angle, 1),
                    "resonancePercent": 84,
                    "camelot": r["camelot"] or "8A",
                    "bpm": r["bpm"] or 120,
                    "genre": r["genre"] or "Инди",
                    "isPlaying": bool(r["is_playing"]),
                    "timestamp": r["updated_at"]
                })

        # Venue Beacons
        cur.execute("SELECT * FROM venue_beacons")
        v_rows = cur.fetchall()
        beacons = []
        for v in v_rows:
            dist = haversine_distance(lat, lon, v["lat"], v["lon"])
            beacons.append({
                "id": v["id"],
                "name": v["name"],
                "venueType": v["venue_type"],
                "currentTrack": v["current_track"],
                "currentArtist": v["current_artist"],
                "streamingSource": v["streaming_source"],
                "distanceMeters": dist,
                "minResonancePercent": v["min_resonance"],
                "proofCode": v["proof_code"],
                "discountDescription": v["discount_description"],
                "address": v["address"] or ""
            })

        # Time capsules
        now = int(time.time())
        cur.execute("SELECT * FROM time_capsules WHERE expires_at > ?", (now,))
        c_rows = cur.fetchall()
        capsules = []
        for c in c_rows:
            dist = haversine_distance(lat, lon, c["lat"], c["lon"])
            capsules.append({
                "id": c["id"],
                "artist": c["artist"],
                "track": c["track"],
                "message": c["message"],
                "distanceMeters": dist,
                "locationName": c["location_name"] or "Сквер",
                "expiresAt": c["expires_at"]
            })

    return {
        "status": "ok",
        "signals": signals,
        "venueBeacons": beacons,
        "timeCapsules": capsules
    }

@app.post("/api/capsule")
def create_capsule(data: CapsuleIn, x_echo_token: Optional[str] = Header(None)):
    token = x_echo_token or "anonymous"
    now = int(time.time())
    expires = now + 7 * 86400
    cid = f"cap-{now}"

    with get_db() as conn:
        conn.execute("""
        INSERT INTO time_capsules (id, artist, track, message, author_label, lat, lon, location_name, created_at, expires_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (cid, data.artist, data.track, data.message, "Анонимный след", data.latitude, data.longitude, data.locationName, now, expires))
        conn.commit()

    return {"status": "ok", "capsuleId": cid}

@app.post("/api/pulse")
def send_pulse(data: PulseIn, x_echo_token: Optional[str] = Header(None)):
    token = x_echo_token or "anonymous"
    now = int(time.time())

    with get_db() as conn:
        conn.execute("""
        INSERT INTO pulses (sender_token, target_signal_id, pulse_type, created_at)
        VALUES (?, ?, ?, ?)
        """, (token, data.targetSignalId, data.pulseType, now))
        conn.commit()

    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("echo_server:app", host="0.0.0.0", port=8000, reload=True)
