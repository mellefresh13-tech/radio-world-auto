from radio_catalog.models import Station, Stream
from radio_catalog.quality import apply_station_stream_quality


def test_station_active_when_any_stream_is_online() -> None:
    station = Station(
        id="x",
        name="Demo",
        country="DE",
        streams=[
            Stream(url="https://example.com/a", source="test", status="offline"),
            Stream(url="https://example.com/b", source="test", status="online"),
        ],
    )

    apply_station_stream_quality([station])

    assert station.status == "active"


def test_station_temporarily_unavailable_when_streams_fail() -> None:
    station = Station(
        id="x",
        name="Demo",
        country="DE",
        streams=[
            Stream(url="https://example.com/a", source="test", status="offline"),
        ],
    )

    apply_station_stream_quality([station])

    assert station.status == "temporarily_unavailable"
