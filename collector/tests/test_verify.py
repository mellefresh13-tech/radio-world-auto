from radio_catalog.verify import _looks_like_hls


def test_hls_detected_from_url() -> None:
    assert _looks_like_hls("application/octet-stream", "https://x.example/live.m3u8", b"")


def test_hls_detected_from_playlist() -> None:
    assert _looks_like_hls("application/octet-stream", "https://x.example/live", b"#EXTM3U\n#EXT-X-VERSION:3")


def test_hls_not_detected_for_mp3() -> None:
    assert not _looks_like_hls("audio/mpeg", "https://x.example/live.mp3", b"ID3")
