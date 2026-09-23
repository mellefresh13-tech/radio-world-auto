from radio_catalog.merge import merge_stations
from radio_catalog.models import SourceRecord, Station, Stream
from datetime import datetime, timezone


def source(provider: str) -> SourceRecord:
    return SourceRecord(
        provider=provider,
        source_id=provider,
        discovered_at=datetime.now(timezone.utc),
    )


def test_merge_same_station_keeps_streams_and_sources() -> None:
    first = Station(
        id="a",
        name="Radio A",
        country="DE",
        homepage="https://radio.example/",
        streams=[Stream(url="https://stream.example/a.mp3", source="rb")],
        sources=[source("radio-browser")],
    )
    second = Station(
        id="b",
        name="Radio A",
        country="DE",
        homepage="https://radio.example",
        streams=[Stream(url="https://stream.example/a.aac", source="iprd")],
        sources=[source("iprd")],
    )

    result = merge_stations([first, second])

    assert len(result) == 1
    assert len(result[0].streams) == 2
    assert {item.provider for item in result[0].sources} == {"radio-browser", "iprd"}
