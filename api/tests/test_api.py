from __future__ import annotations

import asyncio
import json
import sqlite3

import pytest

from fastapi import HTTPException
from fastapi.testclient import TestClient

from radio_api.app import (
    MAX_CONCURRENT_METADATA_REQUESTS,
    _metadata_probe_request_slot,
    _parse_catalog_refresh_seconds,
    app,
)
from radio_api.db import MAX_STATION_OFFSET, connect, has_usable_catalog, initialize, search_stations
from radio_api.metadata_probe import MetadataProbeResult


def seed_db(path) -> None:
    initialize(path)
    with connect(path) as connection:
        connection.execute(
            """
            INSERT INTO stations (
                id, name, country, city, languages_json, genres_json,
                homepage, logo, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                "demo",
                "Demo Radio",
                "DE",
                "Berlin",
                json.dumps(["de"]),
                json.dumps(["Rock"]),
                "https://demo.example",
                None,
                "active",
            ),
        )
        connection.execute(
            """
            INSERT INTO streams (
                station_id, url, codec, bitrate_kbps, is_hls, status, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                "demo",
                "https://stream.example/live.mp3",
                "MP3",
                128,
                0,
                "online",
                "test",
            ),
        )
        connection.commit()


@pytest.mark.parametrize("value", ["0", "-1", "invalid", ""])
def _debug_headers(monkeypatch) -> dict[str, str]:
    token = "test-metadata-debug-token"
    monkeypatch.setenv("METADATA_DEBUG_TOKEN", token)
    return {"Authorization": f"Bearer {token}"}


def test_catalog_refresh_interval_configuration_fails_fast(value: str) -> None:
    with pytest.raises(ValueError, match="CATALOG_REFRESH_SECONDS must be a positive integer"):
        _parse_catalog_refresh_seconds(value)


def test_catalog_refresh_interval_configuration_accepts_positive_integer() -> None:
    assert _parse_catalog_refresh_seconds("21600") == 21600


def test_metadata_debug_endpoint_is_disabled_without_token(monkeypatch) -> None:
    monkeypatch.delenv("METADATA_DEBUG_TOKEN", raising=False)

    response = TestClient(app).get("/debug/metadata")

    assert response.status_code == 503


def test_metadata_debug_endpoint_rejects_invalid_token(monkeypatch) -> None:
    monkeypatch.setenv("METADATA_DEBUG_TOKEN", "expected-token")

    response = TestClient(app).get(
        "/debug/metadata",
        headers={"Authorization": "Bearer wrong-token"},
    )

    assert response.status_code == 401


def test_metadata_probe_rejects_excess_concurrent_requests() -> None:
    slots = [_metadata_probe_request_slot() for _ in range(MAX_CONCURRENT_METADATA_REQUESTS)]
    try:
        for slot in slots:
            assert next(slot) is None

        with pytest.raises(HTTPException) as error:
            next(_metadata_probe_request_slot())
        assert getattr(error.value, "status_code", None) == 429
    finally:
        for slot in reversed(slots):
            slot.close()


def test_catalog_without_online_streams_is_not_usable_fallback(tmp_path) -> None:
    db = tmp_path / "offline-only.db"
    initialize(db)
    with connect(db) as connection:
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

    assert has_usable_catalog(db) is False


def test_incomplete_schema_is_not_accepted_as_usable_fallback_catalog(tmp_path) -> None:
    db = tmp_path / "incomplete.db"
    connection = sqlite3.connect(db)
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

    assert has_usable_catalog(db) is False


def test_health(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    client = TestClient(app)
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["stations"] == 1
    assert response.json()["online_streams"] == 1


def test_station_filter_by_country(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    client = TestClient(app)
    response = client.get("/stations?country=DE")

    assert response.status_code == 200
    assert response.json()["total"] == 1
    assert response.json()["stations"][0]["name"] == "Demo Radio"

def test_station_endpoint_rejects_oversized_offset_before_sqlite(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)

    response = TestClient(app).get(f"/stations?offset={MAX_STATION_OFFSET + 1}")

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["query", "offset"]


def test_metadata_endpoint_rejects_oversized_offset_before_probe(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)

    response = TestClient(app).get(
        f"/debug/metadata?offset={MAX_STATION_OFFSET + 1}",
        headers=_debug_headers(monkeypatch),
    )

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["query", "offset"]


def test_database_station_search_clamps_untrusted_huge_offset(tmp_path) -> None:
    db = tmp_path / "radio.db"
    seed_db(db)

    result = search_stations(db, offset=10**100)

    assert result == []


def test_station_detail(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    client = TestClient(app)
    response = client.get("/stations/demo")

    assert response.status_code == 200
    assert response.json()["streams"][0]["bitrate_kbps"] == 128


def test_station_search_by_genre(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    client = TestClient(app)
    response = client.get("/stations?q=rock")

    assert response.status_code == 200
    assert response.json()["total"] == 1


def test_station_search_by_country(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    client = TestClient(app)
    response = client.get("/stations?q=de")

    assert response.status_code == 200
    assert response.json()["total"] == 1


def test_station_search_casefolds_non_ascii_names_and_genres(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    with connect(db) as connection:
        connection.execute(
            "UPDATE stations SET name = ?, genres_json = ? WHERE id = ?",
            ("РАДИО МАЯК", json.dumps(["ЭЛЕКТРОНИКА"]), "demo"),
        )
        connection.commit()

    client = TestClient(app)
    name_response = client.get("/stations?q=радио")
    genre_response = client.get("/stations?genre=электроника")

    assert name_response.status_code == 200
    assert name_response.json()["total"] == 1
    assert name_response.json()["stations"][0]["name"] == "РАДИО МАЯК"
    assert genre_response.status_code == 200
    assert genre_response.json()["total"] == 1

def test_station_search_treats_like_wildcards_as_literal_characters(
    tmp_path, monkeypatch
) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    literal_name = "100%_Radio\\backup"
    with connect(db) as connection:
        connection.execute(
            "UPDATE stations SET name = ? WHERE id = ?",
            (literal_name, "demo"),
        )
        connection.execute(
            """
            INSERT INTO stations (
                id, name, country, city, languages_json, genres_json,
                homepage, logo, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            ("ordinary", "Ordinary Radio", "DE", "Berlin", "[]", "[]", None, None, "active"),
        )
        connection.execute(
            "INSERT INTO streams (station_id, url, status, source) VALUES (?, ?, ?, ?)",
            ("ordinary", "https://stream.example/ordinary", "online", "test"),
        )
        connection.commit()

    client = TestClient(app)
    percent = client.get("/stations", params={"q": "%"})
    underscore = client.get("/stations", params={"q": "_"})
    backslash = client.get("/stations", params={"q": "\\"})

    for response in (percent, underscore, backslash):
        assert response.status_code == 200
        assert response.json()["total"] == 1
        assert [station["name"] for station in response.json()["stations"]] == [literal_name]




def test_station_list_excludes_unplayable_stations(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    with connect(db) as connection:
        connection.execute(
            """
            INSERT INTO stations (
                id, name, country, city, languages_json, genres_json,
                homepage, logo, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                "offline",
                "Offline Radio",
                "DE",
                "Berlin",
                json.dumps(["de"]),
                json.dumps(["Rock"]),
                None,
                None,
                "active",
            ),
        )
        connection.execute(
            """
            INSERT INTO streams (
                station_id, url, codec, bitrate_kbps, is_hls, status, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                "offline",
                "https://offline.example/live.mp3",
                "MP3",
                128,
                0,
                "offline",
                "test",
            ),
        )
        connection.commit()

    client = TestClient(app)
    response = client.get("/stations")

    assert response.status_code == 200
    payload = response.json()
    assert payload["total"] == 1
    assert [station["name"] for station in payload["stations"]] == ["Demo Radio"]


def test_debug_metadata_applies_one_global_concurrency_limit(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    with connect(db) as connection:
        for station_index in range(1, 5):
            station_id = f"demo-{station_index}"
            connection.execute(
                """
                INSERT INTO stations (
                    id, name, country, city, languages_json, genres_json,
                    homepage, logo, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    station_id,
                    f"Demo Radio {station_index}",
                    "DE",
                    "Berlin",
                    json.dumps(["de"]),
                    json.dumps(["Rock"]),
                    None,
                    None,
                    "active",
                ),
            )
            for stream_index in range(3):
                connection.execute(
                    """
                    INSERT INTO streams (
                        station_id, url, codec, bitrate_kbps, is_hls, status, source
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        station_id,
                        f"https://stream-{station_index}-{stream_index}.example/live.mp3",
                        "MP3",
                        128,
                        0,
                        "online",
                        "test",
                    ),
                )
        # Give the original station two extra streams, for 5 stations × 3 streams.
        for stream_index in range(2):
            connection.execute(
                """
                INSERT INTO streams (
                    station_id, url, codec, bitrate_kbps, is_hls, status, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    "demo",
                    f"https://stream-demo-{stream_index}.example/live.mp3",
                    "MP3",
                    128,
                    0,
                    "online",
                    "test",
                ),
            )
        connection.commit()

    active = 0
    max_active = 0

    async def fake_probe(url: str, *, timeout_seconds: float = 8.0) -> MetadataProbeResult:
        nonlocal active, max_active
        active += 1
        max_active = max(max_active, active)
        try:
            await asyncio.sleep(0.01)
            return MetadataProbeResult(url=url, ok=True, http_status=200)
        finally:
            active -= 1

    monkeypatch.setattr("radio_api.metadata_probe.probe_stream", fake_probe)

    response = TestClient(app).get(
        "/debug/metadata?limit=5&streams_per_station=3&timeout=2",
        headers=_debug_headers(monkeypatch),
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["probed_stations"] == 5
    assert payload["probed_streams"] == 15
    assert all(len(station["streams"]) == 3 for station in payload["stations"])
    assert max_active == 8


def test_debug_metadata_rejects_too_many_probe_urls_before_network_access(
    tmp_path, monkeypatch
) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)

    with connect(db) as connection:
        for index in range(2):
            connection.execute(
                """
                INSERT INTO streams (
                    station_id, url, codec, bitrate_kbps, is_hls, status, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    "demo",
                    f"https://demo-extra-{index}.example/live.mp3",
                    "MP3",
                    128,
                    0,
                    "online",
                    "test",
                ),
            )
        for station_index in range(10):
            station_id = f"probe-{station_index:02d}"
            connection.execute(
                """
                INSERT INTO stations (
                    id, name, country, city, languages_json, genres_json,
                    homepage, logo, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    station_id,
                    f"Test Station {station_index:02d}",
                    "DE",
                    "Berlin",
                    json.dumps(["de"]),
                    json.dumps(["Rock"]),
                    None,
                    None,
                    "active",
                ),
            )
            for stream_index in range(3):
                connection.execute(
                    """
                    INSERT INTO streams (
                        station_id, url, codec, bitrate_kbps, is_hls, status, source
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        station_id,
                        f"https://probe-{station_index}-{stream_index}.example/live.mp3",
                        "MP3",
                        128,
                        0,
                        "online",
                        "test",
                    ),
                )
        connection.commit()

    monkeypatch.setattr(
        "radio_api.app.download_catalog",
        lambda *args: (_ for _ in ()).throw(OSError("offline")),
    )
    monkeypatch.setattr("radio_api.app.start_background_refresh", lambda *args: None)
    probes_called = False

    async def fake_probe_streams(urls, **kwargs):
        nonlocal probes_called
        probes_called = True
        return []

    monkeypatch.setattr("radio_api.app.probe_streams", fake_probe_streams)

    response = TestClient(app).get(
        "/debug/metadata?limit=11&streams_per_station=3&timeout=2",
        headers=_debug_headers(monkeypatch),
    )

    assert response.status_code == 422
    assert "At most 32 stream URLs" in response.json()["detail"]
    assert probes_called is False


def test_startup_recovers_corrupt_local_database_when_download_succeeds(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    db.write_bytes(b"not a SQLite database")
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    download_calls = []
    refresh_calls = []

    def download_valid_catalog(url, target):
        download_calls.append((url, target))
        target.unlink(missing_ok=True)
        seed_db(target)

    monkeypatch.setattr("radio_api.app.download_catalog", download_valid_catalog)
    monkeypatch.setattr(
        "radio_api.app.start_background_refresh",
        lambda *args: refresh_calls.append(args),
    )

    from radio_api import app as app_module
    from radio_api.db import has_usable_catalog

    app_module.startup()

    assert download_calls == [(app_module.CATALOG_DB_URL, db)]
    assert has_usable_catalog(db) is True
    assert refresh_calls == [
        (app_module.CATALOG_DB_URL, db, app_module.CATALOG_REFRESH_SECONDS)
    ]


def test_startup_uses_existing_catalog_if_initial_refresh_fails(tmp_path, monkeypatch) -> None:
    db = tmp_path / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    seed_db(db)
    refresh_calls = []

    def fail_download(url, target):
        raise OSError("catalog host unavailable")

    monkeypatch.setattr("radio_api.app.download_catalog", fail_download)
    monkeypatch.setattr(
        "radio_api.app.start_background_refresh",
        lambda *args: refresh_calls.append(args),
    )

    from radio_api import app as app_module

    app_module.startup()

    assert refresh_calls == [
        (app_module.CATALOG_DB_URL, db, app_module.CATALOG_REFRESH_SECONDS)
    ]


def test_startup_fails_without_existing_catalog_if_initial_refresh_fails(tmp_path, monkeypatch) -> None:
    db = tmp_path / "missing" / "radio.db"
    monkeypatch.setattr("radio_api.app.DB_PATH", db)
    refresh_calls = []

    monkeypatch.setattr(
        "radio_api.app.download_catalog",
        lambda *args: (_ for _ in ()).throw(OSError("catalog host unavailable")),
    )
    monkeypatch.setattr(
        "radio_api.app.start_background_refresh",
        lambda *args: refresh_calls.append(args),
    )

    from radio_api import app as app_module

    try:
        app_module.startup()
    except OSError as exc:
        assert "catalog host unavailable" in str(exc)
    else:
        raise AssertionError("Startup must fail when no usable catalog exists")

    assert refresh_calls == []
