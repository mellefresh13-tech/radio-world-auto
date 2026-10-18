from __future__ import annotations

import sqlite3
from pathlib import Path


MAX_STATION_OFFSET = 1_000_000


REQUIRED_COLUMNS = {
    "stations": frozenset({
        "id", "name", "country", "city", "languages_json", "genres_json",
        "homepage", "logo", "status",
    }),
    "streams": frozenset({
        "id", "station_id", "url", "protocol", "format", "codec",
        "bitrate_kbps", "reliability", "is_hls", "status", "last_checked_at", "source",
    }),
}


SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS stations (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    country TEXT NOT NULL,
    city TEXT,
    languages_json TEXT NOT NULL,
    genres_json TEXT NOT NULL,
    homepage TEXT,
    logo TEXT,
    status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS streams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    station_id TEXT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    protocol TEXT,
    format TEXT,
    codec TEXT,
    bitrate_kbps INTEGER,
    reliability REAL,
    is_hls INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    last_checked_at TEXT,
    source TEXT NOT NULL,
    UNIQUE(station_id, url)
);

CREATE INDEX IF NOT EXISTS idx_stations_country ON stations(country);
CREATE INDEX IF NOT EXISTS idx_stations_status ON stations(status);
CREATE INDEX IF NOT EXISTS idx_streams_station ON streams(station_id);
CREATE INDEX IF NOT EXISTS idx_streams_status ON streams(status);
"""


def connect(db_path: str | Path) -> sqlite3.Connection:
    connection = sqlite3.connect(str(db_path))
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


def has_usable_catalog(db_path: str | Path) -> bool:
    """Return whether a non-empty catalog DB can safely serve cached API responses."""
    path = Path(db_path)
    if not path.is_file():
        return False

    try:
        with connect(path) as connection:
            integrity = connection.execute("PRAGMA quick_check").fetchone()
            if integrity is None or integrity[0] != "ok":
                return False

            tables = {
                row[0]
                for row in connection.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                )
            }
            if not set(REQUIRED_COLUMNS).issubset(tables):
                return False

            for table, required_columns in REQUIRED_COLUMNS.items():
                actual_columns = {
                    row[1]
                    for row in connection.execute(f"PRAGMA table_info({table})")
                }
                if not required_columns.issubset(actual_columns):
                    return False

            station_count = int(
                connection.execute("SELECT COUNT(*) FROM stations").fetchone()[0]
            )
            if station_count <= 0:
                return False

            online_stream_count = int(
                connection.execute(
                    """
                    SELECT COUNT(*)
                    FROM streams st
                    JOIN stations s ON s.id = st.station_id
                    WHERE st.status = 'online'
                    """
                ).fetchone()[0]
            )
            return online_stream_count > 0
    except sqlite3.DatabaseError:
        return False


def initialize(db_path: str | Path) -> None:
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    with connect(db_path) as connection:
        connection.executescript(SCHEMA)


def count_stations(db_path: str | Path) -> int:
    initialize(db_path)
    with connect(db_path) as connection:
        return int(connection.execute("SELECT COUNT(*) FROM stations").fetchone()[0])


def count_online_streams(db_path: str | Path) -> int:
    initialize(db_path)
    with connect(db_path) as connection:
        return int(
            connection.execute(
                "SELECT COUNT(*) FROM streams WHERE status = 'online'"
            ).fetchone()[0]
        )


def search_stations(
    db_path: str | Path,
    *,
    query: str | None = None,
    country: str | None = None,
    genre: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[sqlite3.Row]:
    initialize(db_path)

    clauses = [
        "s.status != 'duplicate'",
        "EXISTS (SELECT 1 FROM streams playable WHERE playable.station_id = s.id AND playable.status = 'online')",
    ]
    params: list[object] = []

    if query:
        clauses.append(
            "(LOWER(s.name) LIKE ? OR LOWER(s.city) LIKE ? "
            "OR LOWER(s.country) LIKE ? OR EXISTS ("
            "SELECT 1 FROM json_each(s.genres_json) WHERE LOWER(value) LIKE ?))"
        )
        needle = f"%{query.casefold()}%"
        params.extend([needle, needle, needle, needle])

    if country:
        clauses.append("s.country = ?")
        params.append(country.upper())

    if genre:
        clauses.append(
            "EXISTS (SELECT 1 FROM json_each(s.genres_json) WHERE LOWER(value) = LOWER(?))"
        )
        params.append(genre)

    sql = f"""
        SELECT s.*,
               1 AS has_online_stream
        FROM stations s
        JOIN (
            SELECT DISTINCT station_id
            FROM streams
            WHERE status = 'online'
        ) playable
          ON playable.station_id = s.id
        WHERE {' AND '.join(clauses)}
        ORDER BY s.name COLLATE NOCASE
        LIMIT ? OFFSET ?
    """
    params.extend([min(max(limit, 1), 200), min(max(offset, 0), MAX_STATION_OFFSET)])

    with connect(db_path) as connection:
        return connection.execute(sql, params).fetchall()
