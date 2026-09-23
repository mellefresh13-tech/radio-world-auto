from __future__ import annotations

import json
import sys
from pathlib import Path

from .db import connect, initialize


def import_snapshot(snapshot_path: str | Path, db_path: str | Path) -> int:
    snapshot = Path(snapshot_path)
    payload = json.loads(snapshot.read_text(encoding="utf-8"))

    if not isinstance(payload, list):
        raise ValueError("Snapshot must contain a JSON array")

    initialize(db_path)

    imported = 0
    with connect(db_path) as connection:
        for station in payload:
            connection.execute(
                """
                INSERT OR REPLACE INTO stations (
                    id, name, country, city, languages_json, genres_json,
                    homepage, logo, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    station["id"],
                    station["name"],
                    station["country"],
                    station.get("city"),
                    json.dumps(station.get("languages", []), ensure_ascii=False),
                    json.dumps(station.get("genres", []), ensure_ascii=False),
                    station.get("homepage"),
                    station.get("logo"),
                    station.get("status", "active"),
                ),
            )

            connection.execute(
                "DELETE FROM streams WHERE station_id = ?",
                (station["id"],),
            )

            for stream in station.get("streams", []):
                connection.execute(
                    """
                    INSERT OR IGNORE INTO streams (
                        station_id, url, protocol, format, codec,
                        bitrate_kbps, reliability, is_hls, status,
                        last_checked_at, source
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        station["id"],
                        stream["url"],
                        stream.get("protocol"),
                        stream.get("format"),
                        stream.get("codec"),
                        stream.get("bitrate_kbps"),
                        stream.get("reliability"),
                        1 if stream.get("is_hls") else 0,
                        stream.get("status", "unknown"),
                        stream.get("last_checked_at"),
                        stream.get("source", "unknown"),
                    ),
                )

            imported += 1

        connection.commit()

    return imported


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: python -m radio_api.importer SNAPSHOT DB")

    print(import_snapshot(sys.argv[1], sys.argv[2]))
