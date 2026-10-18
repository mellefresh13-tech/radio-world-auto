from __future__ import annotations

import json
import sqlite3

import pytest

from radio_api.importer import import_snapshot


def station(station_id: str, name: str, stream_url: str) -> dict:
    return {
        "id": station_id,
        "name": name,
        "country": "DE",
        "streams": [
            {
                "url": stream_url,
                "status": "online",
                "source": "test",
            }
        ],
    }


def station_ids(db_path) -> set[str]:
    with sqlite3.connect(db_path) as connection:
        return {row[0] for row in connection.execute("SELECT id FROM stations")}


def stream_urls(db_path) -> set[str]:
    with sqlite3.connect(db_path) as connection:
        return {row[0] for row in connection.execute("SELECT url FROM streams")}


def test_reimporting_snapshot_removes_stations_missing_from_new_snapshot(tmp_path) -> None:
    snapshot = tmp_path / "stations.json"
    db = tmp_path / "radio.db"
    snapshot.write_text(
        json.dumps(
            [
                station("keep", "Keep FM", "https://example.com/old"),
                station("remove", "Remove FM", "https://example.com/remove"),
            ]
        ),
        encoding="utf-8",
    )
    assert import_snapshot(snapshot, db) == 2
    assert station_ids(db) == {"keep", "remove"}

    snapshot.write_text(
        json.dumps([station("keep", "Keep FM", "https://example.com/new")]),
        encoding="utf-8",
    )
    assert import_snapshot(snapshot, db) == 1

    assert station_ids(db) == {"keep"}
    assert stream_urls(db) == {"https://example.com/new"}


def test_empty_snapshot_is_rejected_without_destroying_existing_catalog(tmp_path) -> None:
    snapshot = tmp_path / "stations.json"
    db = tmp_path / "radio.db"
    snapshot.write_text(
        json.dumps([station("keep", "Keep FM", "https://example.com/live")]),
        encoding="utf-8",
    )
    assert import_snapshot(snapshot, db) == 1

    snapshot.write_text("[]", encoding="utf-8")
    with pytest.raises(ValueError, match="must not be empty"):
        import_snapshot(snapshot, db)

    assert station_ids(db) == {"keep"}
    assert stream_urls(db) == {"https://example.com/live"}


def test_invalid_station_rolls_back_stale_station_deletion(tmp_path) -> None:
    snapshot = tmp_path / "stations.json"
    db = tmp_path / "radio.db"
    snapshot.write_text(
        json.dumps(
            [
                station("keep", "Keep FM", "https://example.com/live"),
                station("stale", "Stale FM", "https://example.com/stale"),
            ]
        ),
        encoding="utf-8",
    )
    assert import_snapshot(snapshot, db) == 2

    snapshot.write_text(json.dumps([{"id": "new-but-invalid"}]), encoding="utf-8")
    with pytest.raises(KeyError, match="name"):
        import_snapshot(snapshot, db)

    assert station_ids(db) == {"keep", "stale"}
    assert stream_urls(db) == {
        "https://example.com/live",
        "https://example.com/stale",
    }
