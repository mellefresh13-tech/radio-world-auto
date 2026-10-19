from radio_catalog import cli
from radio_catalog.models import Station, Stream


def test_curated_stream_occurrence_wins_over_regular_duplicate(monkeypatch) -> None:
    shared_url = "https://stream.example/shared-live"
    ordinary = Station(
        id="ordinary",
        name="Ordinary Radio FM",
        country="DE",
        streams=[Stream(url=shared_url, source="iprd", status="candidate")],
    )
    curated = Station(
        id="curated",
        name="Curated Radio FM",
        country="DE",
        streams=[Stream(url=shared_url, source="curated:important", status="candidate")],
    )
    verified_streams = []

    def fake_verify_streams(streams, *, workers: int, timeout: float):
        verified_streams.extend(streams)
        return list(streams)

    monkeypatch.setattr(cli, "verify_streams", fake_verify_streams)

    cli.verify_catalog(
        [ordinary, curated],
        workers=1,
        timeout=1.0,
        max_streams=0,
    )

    assert len(verified_streams) == 1
    assert verified_streams[0].url == shared_url
    assert verified_streams[0].source == "curated:important"
    assert ordinary.streams[0].last_checked_at is not None
    assert curated.streams[0].last_checked_at is not None
