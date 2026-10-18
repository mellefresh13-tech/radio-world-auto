from radio_catalog.merge import canonical_url, merge_stations
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


def test_merge_deduplicates_canonical_stream_urls_and_source_records() -> None:
    first = Station(
        id="a",
        name="Radio A",
        country="DE",
        homepage="https://radio.example/",
        streams=[
            Stream(url="https://stream.example/a.mp3", source="rb"),
            Stream(url="https://STREAM.example/a.mp3/", source="rb"),
        ],
        sources=[source("radio-browser"), source("radio-browser")],
    )
    second = Station(
        id="b",
        name="Radio A",
        country="DE",
        homepage="https://radio.example",
        streams=[
            Stream(url="https://stream.example/b.mp3", source="iprd"),
            Stream(url="https://stream.example/b.mp3", source="iprd"),
        ],
        sources=[source("iprd"), source("iprd")],
    )

    result = merge_stations([first, second])

    assert len(result) == 1
    assert [stream.url for stream in result[0].streams] == [
        "https://stream.example/a.mp3",
        "https://stream.example/b.mp3",
    ]
    assert {item.provider for item in result[0].sources} == {"radio-browser", "iprd"}
    assert len(result[0].sources) == 2



def test_malformed_external_urls_do_not_abort_station_merge() -> None:
    malformed_homepage = "https://[Broken"
    malformed_stream = "http://[broken"

    station = Station(
        id="malformed-url",
        name="Malformed URL FM",
        country="DE",
        homepage=malformed_homepage,
        streams=[Stream(url=malformed_stream, source="radio-browser")],
        sources=[source("radio-browser")],
    )

    result = merge_stations([station])

    assert len(result) == 1
    assert result[0].homepage == malformed_homepage
    assert result[0].streams[0].url == malformed_stream
    assert canonical_url(malformed_homepage) == malformed_homepage.casefold()
    assert canonical_url(malformed_stream) == malformed_stream.casefold()
