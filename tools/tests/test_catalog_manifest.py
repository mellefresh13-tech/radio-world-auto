import json

import pytest

from tools import build_catalog_delta
from tools.build_catalog_delta import index_unique_stations, playable_station_count


def test_playable_station_count_matches_android_stream_filtering() -> None:
    stations = [
        {
            "id": "online",
            "streams": [{"url": "https://radio.example/live", "status": "online"}],
        },
        {
            "id": "case-insensitive",
            "streams": [{"url": "https://radio.example/live", "status": "ONLINE"}],
        },
        {
            "id": "missing-status-is-playable",
            "streams": [{"url": "https://radio.example/live"}],
        },
        {
            "id": "blank-status-is-playable",
            "streams": [{"url": "https://radio.example/live", "status": "  "}],
        },
        {
            "id": "padded-online-status-is-not-playable",
            "streams": [{"url": "https://radio.example/live", "status": " online "}],
        },
        {
            "id": "numeric-status-is-not-playable",
            "streams": [{"url": "https://radio.example/live", "status": 0}],
        },
        {
            "id": "boolean-status-is-not-playable",
            "streams": [{"url": "https://radio.example/live", "status": False}],
        },
        {
            "id": "one-playable-among-offline-streams",
            "streams": [
                {"url": "https://radio.example/old", "status": "offline"},
                {"url": "https://radio.example/live", "status": "online"},
            ],
        },
        {
            "id": "offline-only",
            "streams": [{"url": "https://radio.example/live", "status": "offline"}],
        },
        {
            "id": "unknown-only",
            "streams": [{"url": "https://radio.example/live", "status": "unknown"}],
        },
        {
            "id": "blank-url",
            "streams": [{"url": "  ", "status": "online"}],
        },
        {"id": "no-streams", "streams": []},
    ]

    # Stations count once, regardless of how many playable streams they expose.
    assert playable_station_count(stations) == 5


def test_playable_station_count_tolerates_missing_or_malformed_stream_lists() -> None:
    stations = [
        {"id": "missing"},
        {"id": "null-streams", "streams": None},
        {"id": "non-object-stream", "streams": [None, "https://radio.example/live"]},
        {"id": "bad-url", "streams": [{"url": None, "status": "online"}]},
    ]

    assert playable_station_count(stations) == 0


def test_unique_station_index_rejects_duplicate_ids_instead_of_silently_dropping_a_record() -> None:
    stations = [
        {"id": "same", "name": "First FM"},
        {"id": "same", "name": "Second FM"},
    ]

    with pytest.raises(ValueError, match="duplicate station id: same"):
        index_unique_stations(stations, label="current catalog")


def test_unique_station_index_rejects_missing_blank_and_whitespace_padded_ids() -> None:
    for station in (
        {"name": "No ID"},
        {"id": ""},
        {"id": "  "},
        {"id": " padded "},
        {"id": 123},
        {"id": True},
    ):
        with pytest.raises(ValueError, match="invalid id"):
            index_unique_stations([station])


def test_unique_station_index_rejects_non_object_station_records() -> None:
    with pytest.raises(ValueError, match="is not an object"):
        index_unique_stations([None])

def test_published_delta_history_uses_immutable_commit_urls(monkeypatch) -> None:
    previous_manifest = {
        "version": "b" * 64,
        "base_version": "a" * 64,
        "updated_count": 3,
        "removed_count": 2,
    }

    def fake_check_output(command, **kwargs):
        if command[:2] == ["git", "log"]:
            return ("1" * 40 + "\n").encode() if not kwargs.get("text") else "1" * 40 + "\n"
        if command == ["git", "show", "1" * 40 + ":data/catalog-manifest.json"]:
            return json.dumps(previous_manifest).encode()
        raise AssertionError(f"Unexpected git command: {command}")

    monkeypatch.setattr(build_catalog_delta.subprocess, "check_output", fake_check_output)
    history = build_catalog_delta.published_delta_history("c" * 64, "b" * 64, 4, 1)

    assert history[0]["version"] == "c" * 64
    assert history[0]["updated_count"] == 4
    assert history[0]["removed_count"] == 1
    assert history[1]["version"] == "b" * 64
    assert history[1]["base_version"] == "a" * 64
    assert history[1]["updated_count"] == 3
    assert history[1]["removed_count"] == 2
    assert history[1]["delta_url"].endswith("/" + "1" * 40 + "/data/catalog-delta.json")
