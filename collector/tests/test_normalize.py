import pytest

from radio_catalog.normalize import normalize_iprd, normalize_radio_browser


def test_normalize_radio_browser() -> None:
    station = normalize_radio_browser(
        {
            "stationuuid": "123",
            "name": "Demo",
            "countrycode": "DE",
            "tags": "Rock, Germany",
            "languagecodes": "deu",
            "url_resolved": "https://example.com/live.mp3",
            "bitrate": 128,
            "hls": 0,
        }
    )

    assert station.country == "DE"
    assert station.streams[0].bitrate_kbps == 128


def test_normalize_iprd() -> None:
    station = normalize_iprd(
        {
            "id": "123",
            "name": "Demo",
            "country": "DE",
            "language": "German",
            "genres": ["rock"],
            "streams": [{"url": "https://example.com/live.mp3", "format": "MP3"}],
        }
    )

    assert station.country == "DE"
    assert station.streams[0].format == "MP3"



def test_normalize_radio_browser_tolerates_invalid_optional_source_fields() -> None:
    station = normalize_radio_browser(
        {
            "stationuuid": "123",
            "name": "Robust FM",
            "countrycode": {"unexpected": "object"},
            "bitrate": "not-a-number",
            "codec": {"unexpected": "object"},
            "hls": "false",
            "url": "https://example.com/live",
        }
    )

    assert station.country == "ZZ"
    assert station.streams[0].bitrate_kbps is None
    assert station.streams[0].codec is None
    assert station.streams[0].is_hls is False


def test_normalize_radio_browser_rejects_missing_or_blank_station_names() -> None:
    with pytest.raises(ValueError, match="non-empty name"):
        normalize_radio_browser({"stationuuid": "123"})
    with pytest.raises(ValueError, match="non-empty name"):
        normalize_radio_browser({"stationuuid": "123", "name": "   "})


def test_normalize_iprd_skips_malformed_streams_and_sanitizes_optional_fields() -> None:
    station = normalize_iprd(
        {
            "id": "123",
            "name": "Robust IPRD FM",
            "country": ["not", "a", "country"],
            "genres": {"invalid": "object"},
            "tags": "Rock, Talk",
            "streams": [
                None,
                "not an object",
                {"url": None},
                {"url": {"unexpected": "object"}},
                {
                    "url": "https://example.com/live",
                    "format": {"invalid": "object"},
                    "bitrate": -128,
                    "reliability": "nan",
                },
            ],
        }
    )

    assert station.country == "ZZ"
    assert station.tags == ["rock", "talk"]
    assert station.genres == ["Rock", "Talk"]
    assert len(station.streams) == 1
    assert station.streams[0].bitrate_kbps is None
    assert station.streams[0].reliability is None
    assert station.streams[0].format is None


def test_normalize_iprd_rejects_non_object_or_unnamed_station_records() -> None:
    with pytest.raises(ValueError, match="non-empty name"):
        normalize_iprd({"id": "123"})
    with pytest.raises(ValueError, match="non-empty name"):
        normalize_iprd({"id": "123", "name": "   "})



def test_normalize_radio_browser_ignores_bitrate_outside_sqlite_integer_range() -> None:
    station = normalize_radio_browser(
        {
            "name": "Too Fast FM",
            "countrycode": "DE",
            "url": "https://example.com/live",
            "bitrate": 10**30,
        }
    )

    assert station.streams[0].bitrate_kbps is None


def test_normalize_iprd_ignores_overflowing_reliability_and_bitrate() -> None:
    station = normalize_iprd(
        {
            "name": "Overflow FM",
            "streams": [
                {
                    "url": "https://example.com/live",
                    "bitrate": 10**30,
                    "reliability": 10**100,
                }
            ],
        }
    )

    assert station.streams[0].bitrate_kbps is None
    assert station.streams[0].reliability is None



def test_normalize_radio_browser_ignores_non_string_stream_url() -> None:
    station = normalize_radio_browser(
        {
            "name": "Invalid Link FM",
            "url": {"unexpected": "object"},
        }
    )

    assert station.streams == []



def test_zero_bitrate_keeps_previous_null_semantics() -> None:
    radio_browser = normalize_radio_browser(
        {
            "name": "Zero Bitrate FM",
            "url": "https://example.com/radio-browser",
            "bitrate": 0,
        }
    )
    iprd = normalize_iprd(
        {
            "name": "Zero Bitrate IPRD",
            "streams": [{"url": "https://example.com/iprd", "bitrate": 0}],
        }
    )

    assert radio_browser.streams[0].bitrate_kbps is None
    assert iprd.streams[0].bitrate_kbps is None
