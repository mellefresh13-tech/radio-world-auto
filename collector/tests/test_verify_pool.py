from radio_catalog.models import Stream
from radio_catalog.verify_pool import verify_streams


def test_empty_verify_pool() -> None:
    assert verify_streams([]) == []


def test_worker_limit_is_applied() -> None:
    streams = [
        Stream(url="https://example.com/a.mp3", source="test"),
        Stream(url="https://example.com/b.mp3", source="test"),
    ]

    result = verify_streams(streams, workers=0)

    assert len(result) == 2
