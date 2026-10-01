from radio_catalog.discovery import extract_stream_candidates


def test_extracts_direct_audio_candidates() -> None:
    html = """
    <a href="https://radio.example/live.mp3">Listen</a>
    <script>
      const streamUrl = "https://cdn.example.com/radio.m3u8";
    </script>
    """

    result = extract_stream_candidates(html, "https://radio.example")

    assert "https://radio.example/live.mp3" in result
    assert "https://cdn.example.com/radio.m3u8" in result
