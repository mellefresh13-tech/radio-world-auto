from __future__ import annotations

import ipaddress
from dataclasses import dataclass
from math import isfinite
from time import monotonic
from urllib.parse import urljoin, urlsplit

import dns.exception
import dns.resolver
import httpx

from .discovery import looks_like_stream_content

AUDIO_MAGIC = (
    b"ID3",
    b"\xff\xfb",
    b"\xff\xf3",
    b"\xff\xf2",
    b"OggS",
)
MAX_REDIRECTS = 5
REDIRECT_STATUSES = frozenset({301, 302, 303, 307, 308})


@dataclass
class StreamCheckResult:
    status: str
    content_type: str | None
    bytes_received: int
    is_hls: bool
    error: str | None = None


def _looks_like_hls(content_type: str | None, url: str, payload: bytes) -> bool:
    lowered_url = url.lower()
    lowered_type = (content_type or "").lower()

    if ".m3u8" in lowered_url:
        return True

    if "mpegurl" in lowered_type or "apple-mpegurl" in lowered_type:
        return True

    return b"#EXTM3U" in payload[:4096]


def _resolve_public_target(
    url: str,
    *,
    timeout: float = 12.0,
) -> ipaddress.IPv4Address | ipaddress.IPv6Address | None:
    """Resolve an HTTP(S) stream target within a bounded DNS lookup deadline."""
    try:
        parsed = urlsplit(url)
        scheme = parsed.scheme.lower()
        if scheme not in {"http", "https"} or not parsed.hostname:
            return None
        if parsed.username is not None or parsed.password is not None:
            return None

        port = parsed.port
        host = parsed.hostname.rstrip(".").lower()
        if not host or host == "localhost" or host.endswith((".localhost", ".local")):
            return None
    except (TypeError, ValueError):
        return None

    if not isinstance(timeout, (int, float)) or not isfinite(timeout) or timeout <= 0:
        return None

    try:
        try:
            literal_address = ipaddress.ip_address(host)
        except ValueError:
            normalized_host = host.encode("idna").decode("ascii")
            resolver = dns.resolver.Resolver()
            # A and AAAA share one deadline; a broken resolver cannot occupy a
            # verification worker indefinitely or exceed the caller's stream timeout.
            deadline = monotonic() + min(timeout, 4.0)
            addresses: set[ipaddress.IPv4Address | ipaddress.IPv6Address] = set()
            for record_type in ("A", "AAAA"):
                remaining = deadline - monotonic()
                if remaining <= 0:
                    return None
                answer = resolver.resolve(
                    normalized_host,
                    record_type,
                    lifetime=remaining,
                    raise_on_no_answer=False,
                )
                if answer.rrset is None:
                    continue
                for record in answer:
                    addresses.add(ipaddress.ip_address(record.address))
        else:
            addresses = {literal_address}
    except (dns.exception.DNSException, OSError, UnicodeError, ValueError):
        return None

    # Reject mixed public/private DNS answers, rather than selecting only a public
    # answer from a hostname that also resolves to internal infrastructure.
    if not addresses or not all(address.is_global for address in addresses):
        return None

    return sorted(addresses, key=lambda address: (address.version, int(address)))[0]


def _host_header(url: str) -> str:
    parsed = urlsplit(url)
    hostname = parsed.hostname or ""
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError:
        host = hostname.encode("idna").decode("ascii")
    else:
        host = f"[{address.compressed}]" if address.version == 6 else address.compressed

    port = parsed.port
    default_port = 443 if parsed.scheme.lower() == "https" else 80
    return f"{host}:{port}" if port is not None and port != default_port else host


def _sni_hostname(url: str) -> bytes:
    hostname = urlsplit(url).hostname or ""
    try:
        ipaddress.ip_address(hostname)
    except ValueError:
        hostname = hostname.encode("idna").decode("ascii")
    return hostname.encode("ascii")


def _offline(error: str) -> StreamCheckResult:
    return StreamCheckResult(
        status="offline",
        content_type=None,
        bytes_received=0,
        is_hls=False,
        error=error,
    )


def check_stream(url: str, timeout: float = 12.0) -> StreamCheckResult:
    try:
        with httpx.Client(
            follow_redirects=False,
            trust_env=False,
            timeout=timeout,
        ) as client:
            current_url = url
            for redirect_count in range(MAX_REDIRECTS + 1):
                target_ip = _resolve_public_target(current_url, timeout=timeout)
                if target_ip is None:
                    return _offline("stream URL does not resolve exclusively to public HTTP(S) addresses")

                original_url = httpx.URL(current_url)
                pinned_url = original_url.copy_with(host=target_ip.compressed)
                headers = {
                    "User-Agent": "RadioWorldAutoCatalog/0.1",
                    "Icy-MetaData": "1",
                    "Accept": "audio/*,application/vnd.apple.mpegurl,*/*;q=0.5",
                    "Host": _host_header(current_url),
                }
                extensions = (
                    {"sni_hostname": _sni_hostname(current_url)}
                    if original_url.scheme == "https"
                    else {}
                )
                request = client.build_request(
                    "GET",
                    pinned_url,
                    headers=headers,
                    extensions=extensions,
                )
                response = client.send(request, stream=True)
                try:
                    if response.status_code in REDIRECT_STATUSES:
                        location = response.headers.get("location")
                        if not location:
                            return _offline("stream redirect omitted Location")
                        if redirect_count >= MAX_REDIRECTS:
                            return _offline("stream exceeded the redirect limit")
                        current_url = urljoin(current_url, location)
                        continue

                    response.raise_for_status()
                    content_type = response.headers.get("content-type")
                    received = 0
                    prefix = bytearray()

                    for chunk in response.iter_bytes(chunk_size=16 * 1024):
                        if len(prefix) < 4096:
                            prefix.extend(chunk[:4096 - len(prefix)])
                        received += len(chunk)
                        if received >= 64 * 1024:
                            break

                    if received == 0:
                        return StreamCheckResult(
                            status="invalid",
                            content_type=content_type,
                            bytes_received=0,
                            is_hls=False,
                            error="stream returned no bytes",
                        )

                    hls = _looks_like_hls(content_type, current_url, bytes(prefix))

                    if hls or looks_like_stream_content(content_type):
                        return StreamCheckResult(
                            status="online",
                            content_type=content_type,
                            bytes_received=received,
                            is_hls=hls,
                        )

                    if bytes(prefix).startswith(AUDIO_MAGIC):
                        return StreamCheckResult(
                            status="online",
                            content_type=content_type,
                            bytes_received=received,
                            is_hls=False,
                        )

                    parsed = urlsplit(current_url)
                    if parsed.path.lower().endswith((".mp3", ".aac", ".ogg", ".opus", ".wav")):
                        return StreamCheckResult(
                            status="online",
                            content_type=content_type,
                            bytes_received=received,
                            is_hls=False,
                        )

                    return StreamCheckResult(
                        status="candidate",
                        content_type=content_type,
                        bytes_received=received,
                        is_hls=False,
                        error="received data but could not confidently classify it as audio",
                    )
                finally:
                    response.close()

            return _offline("stream exceeded the redirect limit")

    except (httpx.HTTPError, httpx.InvalidURL, OSError, UnicodeError, ValueError) as exc:
        return _offline(str(exc))
