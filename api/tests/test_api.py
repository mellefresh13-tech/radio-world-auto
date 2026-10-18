from __future__ import annotations

import asyncio
import json

from fastapi.testclient import TestClient

from radio_api.app import app
from radio_api.db import connect, initialize
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
        "/debug/metadata?limit=5&streams_per_station=3&timeout=2"
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["probed_stations"] == 5
    assert payload["probed_streams"] == 15
    assert all(len(station["streams"]) == 3 for station in payload["stations"])
    assert max_active == 8


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
