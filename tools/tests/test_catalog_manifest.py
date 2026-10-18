from tools.build_catalog_delta import playable_station_count


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
