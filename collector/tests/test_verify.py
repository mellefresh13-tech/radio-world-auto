from __future__ import annotations

import ipaddress
from types import SimpleNamespace

import dns.exception
import httpx

from radio_catalog import verify
from radio_catalog.verify import _looks_like_hls, check_stream


class FakeResponse:
    def __init__(
        self,
        *,
        status_code: int = 200,
        headers: dict[str, str] | None = None,
        payload: bytes = b"ID3\x00audio",
    ) -> None:
        self.status_code = status_code
        self.headers = headers or {"content-type": "audio/mpeg"}
        self.payload = payload
        self.closed = False

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise httpx.HTTPStatusError(
                f"HTTP {self.status_code}",
                request=httpx.Request("GET", "https://stream.example/live"),
                response=httpx.Response(self.status_code),
            )

    def iter_bytes(self, chunk_size: int = 16 * 1024):
        for start in range(0, len(self.payload), chunk_size):
            yield self.payload[start : start + chunk_size]

    def close(self) -> None:
        self.closed = True


class FakeClient:
    def __init__(self, responses: list[FakeResponse], **kwargs) -> None:
        self.responses = list(responses)
        self.options = kwargs
        self.requests: list[dict] = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def build_request(self, method, url, *, headers=None, extensions=None):
        return {
            "method": method,
            "url": url,
            "headers": headers or {},
            "extensions": extensions or {},
        }

    def send(self, request, *, stream=False):
        self.requests.append(request)
        return self.responses[len(self.requests) - 1]


def test_hls_detected_from_url() -> None:
    assert _looks_like_hls("application/octet-stream", "https://x.example/live.m3u8", b"")


def test_hls_detected_from_playlist() -> None:
    assert _looks_like_hls("application/octet-stream", "https://x.example/live", b"#EXTM3U\n#EXT-X-VERSION:3")


def test_hls_not_detected_for_mp3() -> None:
    assert not _looks_like_hls("audio/mpeg", "https://x.example/live.mp3", b"ID3")


def test_malformed_stream_url_is_offline() -> None:
    result = check_stream("http://")
    assert result.status == "offline"


def test_invalid_port_stream_url_is_offline() -> None:
    result = check_stream("https://example.com:https/live")
    assert result.status == "offline"


def test_public_target_guard_rejects_local_and_private_addresses() -> None:
    assert verify._resolve_public_target("http://127.0.0.1/live") is None
    assert verify._resolve_public_target("http://10.0.0.1/live") is None
    assert verify._resolve_public_target("http://169.254.169.254/latest/meta-data") is None
    assert verify._resolve_public_target("http://localhost/live") is None
    assert verify._resolve_public_target("file:///etc/passwd") is None
    assert verify._resolve_public_target("https://user:pass@8.8.8.8/live") is None
    assert verify._resolve_public_target("https://8.8.8.8/live") == ipaddress.ip_address("8.8.8.8")


def test_private_initial_stream_url_is_rejected_without_request(monkeypatch) -> None:
    client = FakeClient([])
    monkeypatch.setattr(verify.httpx, "Client", lambda **kwargs: client)

    result = check_stream("http://127.0.0.1/private")

    assert result.status == "offline"
    assert client.requests == []

def test_public_dns_answers_are_allowed_but_mixed_private_answers_are_rejected(monkeypatch) -> None:
    class FakeResolver:
        def __init__(self, records: dict[str, list[str]]) -> None:
            self.records = records
            self.calls: list[tuple[str, float]] = []

        def resolve(self, hostname, record_type, *, lifetime, raise_on_no_answer):
            self.calls.append((record_type, lifetime))
            addresses = self.records.get(record_type, [])
            answer_records = [SimpleNamespace(address=address) for address in addresses]
            return SimpleNamespace(rrset=answer_records or None), iter(answer_records)

    # dnspython Answer objects are iterable and also expose rrset.
    class FakeAnswer:
        def __init__(self, addresses: list[str]) -> None:
            self.rrset = [SimpleNamespace(address=address) for address in addresses] or None

        def __iter__(self):
            return iter(self.rrset or [])

    class Resolver:
        def __init__(self, records: dict[str, list[str]]) -> None:
            self.records = records
            self.calls: list[tuple[str, float]] = []

        def resolve(self, hostname, record_type, *, lifetime, raise_on_no_answer):
            self.calls.append((record_type, lifetime))
            return FakeAnswer(self.records.get(record_type, []))

    public_resolver = Resolver({"A": ["93.184.216.34"], "AAAA": []})
    monkeypatch.setattr(verify.dns.resolver, "Resolver", lambda: public_resolver)

    assert verify._resolve_public_target("https://radio.example/live", timeout=2.0) == ipaddress.ip_address(
        "93.184.216.34"
    )
    assert [kind for kind, _ in public_resolver.calls] == ["A", "AAAA"]
    assert all(0 < lifetime <= 2.0 for _, lifetime in public_resolver.calls)

    mixed_resolver = Resolver({"A": ["93.184.216.34"], "AAAA": ["10.0.0.4"]})
    monkeypatch.setattr(verify.dns.resolver, "Resolver", lambda: mixed_resolver)
    assert verify._resolve_public_target("https://radio.example/live", timeout=2.0) is None


def test_dns_timeout_fails_closed_within_the_callers_budget(monkeypatch) -> None:
    lifetimes: list[float] = []

    class SlowResolver:
        def resolve(self, hostname, record_type, *, lifetime, raise_on_no_answer):
            lifetimes.append(lifetime)
            raise dns.exception.Timeout("test DNS timeout")

    monkeypatch.setattr(verify.dns.resolver, "Resolver", SlowResolver)

    assert verify._resolve_public_target("https://radio.example/live", timeout=0.25) is None
    assert len(lifetimes) == 1
    assert 0 < lifetimes[0] <= 0.25


def test_private_redirect_target_is_rejected_before_following(monkeypatch) -> None:
    redirect = FakeResponse(
        status_code=302,
        headers={"location": "http://127.0.0.1/admin"},
        payload=b"",
    )
    client = FakeClient([redirect])
    monkeypatch.setattr(verify.httpx, "Client", lambda **kwargs: client)
    monkeypatch.setattr(
        verify,
        "_resolve_public_target",
        lambda url, timeout=12.0: None if "127.0.0.1" in url else ipaddress.ip_address("93.184.216.34"),
    )

    result = check_stream("https://radio.example/start")

    assert result.status == "offline"
    assert len(client.requests) == 1
    assert str(client.requests[0]["url"]) == "https://93.184.216.34/start"
    assert client.requests[0]["headers"]["Host"] == "radio.example"
    assert client.requests[0]["extensions"]["sni_hostname"] == b"radio.example"
    assert redirect.closed is True


def test_public_redirect_is_followed_with_host_and_sni_preserved(monkeypatch) -> None:
    redirect = FakeResponse(
        status_code=302,
        headers={"location": "https://cdn.example/live.mp3"},
        payload=b"",
    )
    final = FakeResponse(headers={"content-type": "audio/mpeg"}, payload=b"ID3live-audio")
    client = FakeClient([redirect, final])

    def make_client(**kwargs):
        client.options.update(kwargs)
        return client

    monkeypatch.setattr(verify.httpx, "Client", make_client)
    monkeypatch.setattr(
        verify,
        "_resolve_public_target",
        lambda url, timeout=12.0: ipaddress.ip_address(
            "1.1.1.1" if "cdn.example" in url else "93.184.216.34"
        ),
    )

    result = check_stream("https://radio.example/start")

    assert result.status == "online"
    assert result.bytes_received > 0
    assert client.options["follow_redirects"] is False
    assert client.options["trust_env"] is False
    assert [str(request["url"]) for request in client.requests] == [
        "https://93.184.216.34/start",
        "https://1.1.1.1/live.mp3",
    ]
    assert [request["headers"]["Host"] for request in client.requests] == [
        "radio.example",
        "cdn.example",
    ]
    assert [request["extensions"]["sni_hostname"] for request in client.requests] == [
        b"radio.example",
        b"cdn.example",
    ]
    assert redirect.closed is True
    assert final.closed is True


def test_redirect_without_location_or_over_limit_is_offline(monkeypatch) -> None:
    missing_location = FakeResponse(status_code=302, headers={}, payload=b"")
    client = FakeClient([missing_location])
    monkeypatch.setattr(verify.httpx, "Client", lambda **kwargs: client)
    monkeypatch.setattr(
        verify, "_resolve_public_target", lambda url, timeout=12.0: ipaddress.ip_address("93.184.216.34")
    )

    result = check_stream("https://radio.example/start")

    assert result.status == "offline"
    assert "Location" in (result.error or "")
    assert missing_location.closed is True

def test_redirect_chain_is_limited(monkeypatch) -> None:
    responses = [
        FakeResponse(status_code=302, headers={"location": "/next"}, payload=b"")
        for _ in range(verify.MAX_REDIRECTS + 1)
    ]
    client = FakeClient(responses)
    monkeypatch.setattr(verify.httpx, "Client", lambda **kwargs: client)
    monkeypatch.setattr(
        verify, "_resolve_public_target", lambda url: ipaddress.ip_address("93.184.216.34")
    )

    result = check_stream("https://radio.example/start")

    assert result.status == "offline"
    assert "redirect limit" in (result.error or "")
    assert len(client.requests) == verify.MAX_REDIRECTS + 1
    assert all(response.closed for response in responses)
