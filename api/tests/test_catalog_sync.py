from __future__ import annotations

import sqlite3

import pytest

from radio_api.catalog_sync import download_catalog


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
    assert not target.with_name(target.name + ".tmp").exists()


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


def test_download_accepts_nonempty_sqlite_catalog(tmp_path, monkeypatch) -> None:
    candidate = tmp_path / "candidate.db"
    connection = sqlite3.connect(candidate)
    try:
        connection.executescript(
            """
            CREATE TABLE stations (id TEXT PRIMARY KEY, name TEXT NOT NULL);
            CREATE TABLE streams (id INTEGER PRIMARY KEY, station_id TEXT NOT NULL);
            INSERT INTO stations (id, name) VALUES ('demo', 'Demo Radio');
            """
        )
        connection.commit()
    finally:
        connection.close()

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
