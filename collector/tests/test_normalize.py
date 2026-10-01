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
