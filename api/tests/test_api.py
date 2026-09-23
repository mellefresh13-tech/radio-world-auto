from __future__ import annotations

import json

from fastapi.testclient import TestClient

from radio_api.app import app
from radio_api.db import connect, initialize


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
