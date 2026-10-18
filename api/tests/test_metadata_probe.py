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


class FakeRedirectResponse:
    status_code = 302

    def __init__(self, url: str, location: str) -> None:
        self.url = metadata_probe.httpx.URL(url)
        self.headers = {"location": location}

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False


class FakeAsyncClient:
    def __init__(self, response, **kwargs) -> None:
        self.response = response

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    def stream(self, method: str, url: str):
        return self.response


class FakeSequenceAsyncClient:
    def __init__(self, responses, **kwargs) -> None:
        self.responses = list(responses)
        self.requested_urls: list[str] = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    def stream(self, method: str, url: str):
        self.requested_urls.append(str(url))
        return self.responses[len(self.requested_urls) - 1]


def _allow_public_targets(monkeypatch, predicate=None) -> None:
    async def validate(url: str) -> bool:
        return True if predicate is None else predicate(url)

    monkeypatch.setattr(metadata_probe, "_is_public_http_url", validate)


def test_probe_rejects_oversized_icy_metaint_before_reading_stream_body(monkeypatch) -> None:
    response = FakeResponse(metadata_probe.MAX_ICY_METAINT_BYTES + 1)
    monkeypatch.setattr(
        metadata_probe.httpx,
        "AsyncClient",
        lambda **kwargs: FakeAsyncClient(response, **kwargs),
    )
    _allow_public_targets(monkeypatch)

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
    _allow_public_targets(monkeypatch)

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


def test_probe_applies_total_deadline_to_slow_drip_stream(monkeypatch) -> None:
    class SlowFakeResponse(FakeResponse):
        async def aiter_bytes(self):
            self.body_reads += 1
            yield b"ab"
            await asyncio.sleep(0.05)
            yield b"cd" + b"\\x01" + b"StreamTitle='X';"

    response = SlowFakeResponse(metaint=4)
    monkeypatch.setattr(
        metadata_probe.httpx,
        "AsyncClient",
        lambda **kwargs: FakeAsyncClient(response, **kwargs),
    )
    _allow_public_targets(monkeypatch)

    result = asyncio.run(
        metadata_probe.probe_stream(response.url, timeout_seconds=0.01)
    )

    assert result.ok is True
    assert result.error == "timeout"
    assert result.stream_title is None
    assert response.body_reads == 1


def test_public_target_guard_rejects_loopback_private_and_non_http_addresses() -> None:
    async def check_targets():
        return {
            url: await metadata_probe._is_public_http_url(url)
            for url in (
                "http://127.0.0.1/stream",
                "http://10.0.0.5/stream",
                "http://169.254.169.254/latest/meta-data",
                "https://[::1]/stream",
                "http://localhost/stream",
                "file:///etc/passwd",
            )
        }

    results = asyncio.run(check_targets())

    assert all(allowed is False for allowed in results.values())


def test_probe_does_not_request_a_private_initial_target(monkeypatch) -> None:
    client = FakeSequenceAsyncClient([])
    monkeypatch.setattr(
        metadata_probe.httpx,
        "AsyncClient",
        lambda **kwargs: client,
    )

    result = asyncio.run(metadata_probe.probe_stream("http://127.0.0.1/private"))

    assert result.error == "non_public_target"
    assert client.requested_urls == []


def test_probe_blocks_private_redirect_before_sending_second_request(monkeypatch) -> None:
    redirect = FakeRedirectResponse(
        "https://stream.example/live",
        "http://127.0.0.1/admin",
    )
    client = FakeSequenceAsyncClient([redirect])
    monkeypatch.setattr(
        metadata_probe.httpx,
        "AsyncClient",
        lambda **kwargs: client,
    )
    _allow_public_targets(monkeypatch, lambda url: "127.0.0.1" not in url)

    result = asyncio.run(metadata_probe.probe_stream("https://stream.example/live"))

    assert result.error == "non_public_target"
    assert client.requested_urls == ["https://stream.example/live"]


def test_probe_follows_redirect_to_another_public_host(monkeypatch) -> None:
    redirect = FakeRedirectResponse(
        "https://stream.example/start",
        "https://cdn.example/live",
    )
    final = FakeResponse(metaint=4, payload=b"abcd\\x00")
    final.url = metadata_probe.httpx.URL("https://cdn.example/live")
    client = FakeSequenceAsyncClient([redirect, final])
    monkeypatch.setattr(
        metadata_probe.httpx,
        "AsyncClient",
        lambda **kwargs: client,
    )
    _allow_public_targets(monkeypatch)

    result = asyncio.run(metadata_probe.probe_stream("https://stream.example/start"))

    assert client.requested_urls == [
        "https://stream.example/start",
        "https://cdn.example/live",
    ]
    assert result.redirected_url == "https://cdn.example/live"
    assert result.http_status == 200
    assert result.error == "empty_icy_metadata"
