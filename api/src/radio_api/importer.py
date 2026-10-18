from __future__ import annotations

import json
import sys
from pathlib import Path

from .db import connect, initialize


def _validated_station_ids(payload: list[object]) -> set[str]:
    ids: set[str] = set()
    for index, station in enumerate(payload):
        if not isinstance(station, dict):
            raise ValueError(f"Snapshot station at index {index} is not an object")

        raw_id = station.get("id")
        if not isinstance(raw_id, str) or not raw_id.strip() or raw_id != raw_id.strip():
            raise ValueError(f"Snapshot station at index {index} has an invalid id")
        if raw_id in ids:
            raise ValueError(f"Snapshot contains duplicate station id: {raw_id}")
        ids.add(raw_id)
    return ids


def import_snapshot(snapshot_path: str | Path, db_path: str | Path) -> int:
    snapshot = Path(snapshot_path)
    payload = json.loads(snapshot.read_text(encoding="utf-8"))

    if not isinstance(payload, list):
        raise ValueError("Snapshot must contain a JSON array")
    if not payload:
        raise ValueError("Snapshot must not be empty")
    incoming_ids = _validated_station_ids(payload)

    initialize(db_path)

    imported = 0
    with connect(db_path) as connection:
        existing_ids = {
            row["id"] for row in connection.execute("SELECT id FROM stations")
        }
        stale_ids = existing_ids - incoming_ids
        connection.executemany(
            "DELETE FROM stations WHERE id = ?",
            ((station_id,) for station_id in stale_ids),
        )

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
