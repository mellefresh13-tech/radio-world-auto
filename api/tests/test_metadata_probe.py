from __future__ import annotations

import asyncio

from radio_api import metadata_probe


class FakeResponse:
    status_code = 200
    url = "https://stream.example/live"

    def __init__(self, metaint: int, payload: bytes = b"") -> None:
        self.headers = {
            "content-type": "audio/mpeg",
            "icy-metaint": str(metaint),
        }
        self.payload = payload
        self.body_reads = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    async def aiter_bytes(self):
        self.body_reads += 1
        if self.payload:
            yield self.payload


class FakeAsyncClient:
    def __init__(self, response: FakeResponse, **kwargs) -> None:
        self.response = response

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    def stream(self, method: str, url: str):
        return self.response


def test_probe_rejects_oversized_icy_metaint_before_reading_stream_body(monkeypatch) -> None:
    response = FakeResponse(metadata_probe.MAX_ICY_METAINT_BYTES + 1)
    monkeypatch.setattr(
        metadata_probe.httpx,
        "AsyncClient",
        lambda **kwargs: FakeAsyncClient(response, **kwargs),
    )

    result = asyncio.run(metadata_probe.probe_stream(response.url))

    assert result.ok is True
    assert result.icy_metaint == metadata_probe.MAX_ICY_METAINT_BYTES + 1
    assert result.error == "icy_metaint_too_large"
    assert response.body_reads == 0


def test_probe_still_parses_normal_icy_track_metadata(monkeypatch) -> None:
    metadata = b"StreamTitle='X';"
    assert len(metadata) == 16
    response = FakeResponse(metaint=4, payload=b"abcd" + b"\x01" + metadata)
    monkeypatch.setattr(
        metadata_probe.httpx,
        "AsyncClient",
        lambda **kwargs: FakeAsyncClient(response, **kwargs),
    )

    result = asyncio.run(metadata_probe.probe_stream(response.url))

    assert result.ok is True
    assert result.metadata_protocol == "icy"
    assert result.stream_title == "X"
    assert result.has_track_metadata is True
    assert result.error is None
    assert response.body_reads == 1


def test_probe_streams_global_limit_is_shared_across_simultaneous_calls(monkeypatch) -> None:
    active = 0
    max_active = 0

    async def fake_probe(url: str, *, timeout_seconds: float = 8.0):
        nonlocal active, max_active
        active += 1
        max_active = max(max_active, active)
        try:
            await asyncio.sleep(0.01)
            return metadata_probe.MetadataProbeResult(url=url, ok=True, http_status=200)
        finally:
            active -= 1

    monkeypatch.setattr(metadata_probe, "probe_stream", fake_probe)

    async def run_batches():
        first = [f"https://first-{index}.example/live" for index in range(10)]
        second = [f"https://second-{index}.example/live" for index in range(10)]
        return await asyncio.gather(
            metadata_probe.probe_streams(first, concurrency=8),
            metadata_probe.probe_streams(second, concurrency=8),
        )

    results = asyncio.run(run_batches())

    assert sum(len(batch) for batch in results) == 20
    assert max_active == metadata_probe.MAX_GLOBAL_PROBE_CONCURRENCY
