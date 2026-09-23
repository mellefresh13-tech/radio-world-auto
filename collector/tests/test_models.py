from radio_catalog.models import Station, Stream


def test_station_accepts_stream() -> None:
    station = Station(
        id="demo",
        name="Demo Radio",
        country="DE",
        streams=[Stream(url="https://example.com/live.mp3", source="test")],
    )

    assert station.country == "DE"
    assert station.streams[0].source == "test"
