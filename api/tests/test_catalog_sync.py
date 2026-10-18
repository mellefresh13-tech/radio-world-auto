from __future__ import annotations

import sqlite3
import threading
from concurrent.futures import ThreadPoolExecutor

import pytest

from radio_api.catalog_sync import download_catalog, start_background_refresh
from radio_api.db import connect, initialize


class FakeResponse:
    def __init__(self, payload: bytes):
        self.payload = payload
        self.offset = 0

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def read(self, size: int) -> bytes:
        chunk = self.payload[self.offset : self.offset + size]
        self.offset += len(chunk)
        return chunk


def test_download_rejects_invalid_database_without_replacing_current_file(
    tmp_path, monkeypatch
) -> None:
    target = tmp_path / "radio.db"
    original = b"previous known-good database contents"
    target.write_bytes(original)
    payload = b"<html><body>not a SQLite database</body></html>" * 200

    monkeypatch.setattr(
        "radio_api.catalog_sync.urllib.request.urlopen",
        lambda *args, **kwargs: FakeResponse(payload),
    )

    with pytest.raises(RuntimeError, match="valid SQLite database"):
        download_catalog("https://catalog.example/radio.db", target)

    assert target.read_bytes() == original
    assert list(target.parent.glob(f"{target.name}.*.tmp")) == []


def test_download_rejects_oversized_payload_without_replacing_current_file(
    tmp_path, monkeypatch
) -> None:
    target = tmp_path / "radio.db"
    original = b"known-good database"
    target.write_bytes(original)

    monkeypatch.setattr("radio_api.catalog_sync.MAX_CATALOG_DOWNLOAD_BYTES", 1024)
    monkeypatch.setattr(
        "radio_api.catalog_sync.urllib.request.urlopen",
        lambda *args, **kwargs: FakeResponse(b"x" * 8192),
    )

    with pytest.raises(RuntimeError, match="exceeds maximum allowed size"):
        download_catalog("https://catalog.example/radio.db", target)

    assert target.read_bytes() == original
    assert not target.with_name(target.name + ".tmp").exists()


def test_download_rejects_database_with_missing_required_columns(
    tmp_path, monkeypatch
) -> None:
    target = tmp_path / "radio.db"
    original = b"previous known-good database"
    target.write_bytes(original)

    candidate = tmp_path / "candidate.db"
    connection = sqlite3.connect(candidate)
    try:
        connection.executescript(
            """
            CREATE TABLE stations (id TEXT PRIMARY KEY, name TEXT NOT NULL);
            CREATE TABLE streams (id INTEGER PRIMARY KEY, station_id TEXT NOT NULL);
            CREATE TABLE padding (value TEXT);
            INSERT INTO stations (id, name) VALUES ('demo', 'Demo Radio');
            """
        )
        connection.execute(
            "INSERT INTO padding (value) VALUES (?)",
            ("x" * 8192,),
        )
        connection.commit()
    finally:
        connection.close()

    payload = candidate.read_bytes()
    assert len(payload) >= 4096
    monkeypatch.setattr(
        "radio_api.catalog_sync.urllib.request.urlopen",
        lambda *args, **kwargs: FakeResponse(payload),
    )

    with pytest.raises(RuntimeError, match="missing required columns"):
        download_catalog("https://catalog.example/radio.db", target)

    assert target.read_bytes() == original
    assert not target.with_name(target.name + ".tmp").exists()


def test_concurrent_downloads_use_distinct_temporary_files(tmp_path, monkeypatch) -> None:
    candidate = tmp_path / "candidate.db"
    initialize(candidate)
    with connect(candidate) as connection:
        connection.execute(
            """
            INSERT INTO stations (
                id, name, country, city, languages_json, genres_json,
                homepage, logo, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            ("demo", "Demo Radio", "DE", "Berlin", "[]", "[]", None, None, "active"),
        )
        connection.execute(
            "INSERT INTO streams (station_id, url, status, source) VALUES (?, ?, ?, ?)",
            ("demo", "https://stream.example/live", "online", "test"),
        )
        connection.commit()
    payload = candidate.read_bytes()
    target = tmp_path / "published.db"
    monkeypatch.setattr(
        "radio_api.catalog_sync.urllib.request.urlopen",
        lambda *args, **kwargs: FakeResponse(payload),
    )

    validation_barrier = threading.Barrier(2)
    validated_paths = []

    def synchronize_validation(path) -> None:
        validated_paths.append(path)
        validation_barrier.wait(timeout=5)

    monkeypatch.setattr(
        "radio_api.catalog_sync._validate_catalog_database",
        synchronize_validation,
    )

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(
            executor.map(
                lambda _: download_catalog("https://catalog.example/radio.db", target),
                range(2),
            )
        )

    assert results == [None, None]
    assert len(set(validated_paths)) == 2
    assert target.read_bytes() == payload
    assert list(target.parent.glob(f"{target.name}.*.tmp")) == []


@pytest.mark.parametrize(
    "interval_seconds",
    [0, -1, int(threading.TIMEOUT_MAX) + 1],
)
def test_background_refresh_rejects_unsupported_interval(
    tmp_path, interval_seconds: int
) -> None:
    with pytest.raises(ValueError, match="interval_seconds must be positive"):
        start_background_refresh(
            "https://catalog.example/radio.db",
            tmp_path / "radio.db",
            interval_seconds,
        )


def test_download_rejects_catalog_without_online_streams(
    tmp_path, monkeypatch
) -> None:
    target = tmp_path / "radio.db"
    original = b"previous known-good database"
    target.write_bytes(original)

    candidate = tmp_path / "candidate.db"
    initialize(candidate)
    with connect(candidate) as connection:
        connection.execute(
            """
            INSERT INTO stations (
                id, name, country, city, languages_json, genres_json,
                homepage, logo, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            ("offline", "Offline Radio", "DE", "Berlin", "[]", "[]", None, None, "temporarily_unavailable"),
        )
        connection.execute(
            "INSERT INTO streams (station_id, url, status, source) VALUES (?, ?, ?, ?)",
            ("offline", "https://stream.example/offline", "offline", "test"),
        )
        connection.commit()

    payload = candidate.read_bytes()
    assert len(payload) >= 4096
    monkeypatch.setattr(
        "radio_api.catalog_sync.urllib.request.urlopen",
        lambda *args, **kwargs: FakeResponse(payload),
    )

    with pytest.raises(RuntimeError, match="contains no online streams"):
        download_catalog("https://catalog.example/radio.db", target)

    assert target.read_bytes() == original
    assert list(target.parent.glob(f"{target.name}.*.tmp")) == []


def test_download_accepts_nonempty_sqlite_catalog(tmp_path, monkeypatch) -> None:
    candidate = tmp_path / "candidate.db"
    initialize(candidate)
    with connect(candidate) as connection:
        connection.execute(
            """
            INSERT INTO stations (
                id, name, country, city, languages_json, genres_json,
                homepage, logo, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            ("demo", "Demo Radio", "DE", "Berlin", "[]", "[]", None, None, "active"),
        )
        connection.execute(
            """
            INSERT INTO streams (station_id, url, status, source)
            VALUES (?, ?, ?, ?)
            """,
            ("demo", "https://stream.example/live", "online", "test"),
        )
        connection.commit()

    payload = candidate.read_bytes()
    assert len(payload) >= 4096
    target = tmp_path / "published.db"
    monkeypatch.setattr(
        "radio_api.catalog_sync.urllib.request.urlopen",
        lambda *args, **kwargs: FakeResponse(payload),
    )

    download_catalog("https://catalog.example/radio.db", target)

    assert target.read_bytes() == payload
    check = sqlite3.connect(target)
    try:
        assert check.execute("SELECT COUNT(*) FROM stations").fetchone()[0] == 1
    finally:
        check.close()
