from __future__ import annotations

import asyncio
import ipaddress
import re
import socket
import threading
import weakref
from dataclasses import dataclass
from urllib.parse import urlparse

import httpx


USER_AGENT = "RadioWorldAuto/metadata-probe"
DEFAULT_TIMEOUT_SECONDS = 8.0
MAX_ICY_METAINT_BYTES = 1024 * 1024
MAX_GLOBAL_PROBE_CONCURRENCY = 8
MAX_REDIRECTS = 5
REDIRECT_STATUSES = frozenset({301, 302, 303, 307, 308})
TITLE_PATTERN = re.compile(r"StreamTitle='(.*?)';", re.IGNORECASE | re.DOTALL)

_loop_semaphores: weakref.WeakKeyDictionary[
    asyncio.AbstractEventLoop, weakref.ReferenceType[asyncio.Semaphore]
] = weakref.WeakKeyDictionary()
_loop_semaphores_lock = threading.Lock()


def _global_probe_semaphore() -> asyncio.Semaphore:
    loop = asyncio.get_running_loop()
    with _loop_semaphores_lock:
        semaphore_ref = _loop_semaphores.get(loop)
        semaphore = semaphore_ref() if semaphore_ref is not None else None
        if semaphore is None:
            semaphore = asyncio.Semaphore(MAX_GLOBAL_PROBE_CONCURRENCY)
            # Weakly reference both loop and semaphore: a semaphore may bind
            # itself to the loop when callers queue, so a strong WeakKeyDictionary
            # value could otherwise keep a closed test/server loop alive.
            _loop_semaphores[loop] = weakref.ref(semaphore)
        return semaphore


@dataclass
class MetadataProbeResult:
    url: str
    ok: bool = False
    http_status: int | None = None
    content_type: str | None = None
    redirected_url: str | None = None
    icy_metaint: int | None = None
    icy_name: str | None = None
    icy_genre: str | None = None
    icy_br: str | None = None
    icy_url: str | None = None
    metadata_protocol: str | None = None
    stream_title: str | None = None
    has_track_metadata: bool = False
    raw_metadata: str | None = None
    error: str | None = None

    def as_dict(self) -> dict:
        return {
            "url": self.url,
            "ok": self.ok,
            "http_status": self.http_status,
            "content_type": self.content_type,
            "redirected_url": self.redirected_url,
            "icy_metaint": self.icy_metaint,
            "icy_name": self.icy_name,
            "icy_genre": self.icy_genre,
            "icy_br": self.icy_br,
            "icy_url": self.icy_url,
            "metadata_protocol": self.metadata_protocol,
            "stream_title": self.stream_title,
            "has_track_metadata": self.has_track_metadata,
            "raw_metadata": self.raw_metadata,
            "error": self.error,
        }


def _clean(value: str | None) -> str | None:
    if value is None:
        return None
    value = value.strip().replace("\x00", "")
    return value or None


def _parse_title(metadata: bytes) -> tuple[str | None, str | None]:
    text = metadata.decode("utf-8", errors="replace").replace("\x00", "")
    match = TITLE_PATTERN.search(text)
    if not match:
        return None, text.strip() or None
    return _clean(match.group(1)), text.strip() or None


class _ByteReader:
    def __init__(self, response: httpx.Response, *, timeout_seconds: float):
        self._iterator = response.aiter_bytes().__aiter__()
        self._buffer = bytearray()
        self._deadline = asyncio.get_running_loop().time() + timeout_seconds

    async def read_exact(self, size: int) -> bytes:
        while len(self._buffer) < size:
            try:
                remaining = self._deadline - asyncio.get_running_loop().time()
                if remaining <= 0:
                    raise asyncio.TimeoutError
                chunk = await asyncio.wait_for(
                    self._iterator.__anext__(),
                    timeout=remaining,
                )
            except StopAsyncIteration:
                break
            if chunk:
                self._buffer.extend(chunk)

        data = bytes(self._buffer[:size])
        del self._buffer[:size]
        return data


async def _is_public_http_url(url: str) -> bool:
    """Reject probe targets that can address a local or non-public network."""
    try:
        parsed = urlparse(url)
        scheme = parsed.scheme.lower()
        if scheme not in {"http", "https"} or not parsed.hostname:
            return False
        port = parsed.port
        host = parsed.hostname.rstrip(".").lower()
        if not host or host == "localhost" or host.endswith((".localhost", ".local")):
            return False
    except (TypeError, ValueError):
        return False

    port = port or (443 if scheme == "https" else 80)
    try:
        try:
            literal_address = ipaddress.ip_address(host)
        except ValueError:
            normalized_host = host.encode("idna").decode("ascii")
            address_info = await asyncio.get_running_loop().getaddrinfo(
                normalized_host,
                port,
                type=socket.SOCK_STREAM,
            )
            addresses = {
                ipaddress.ip_address(info[4][0].split("%", 1)[0])
                for info in address_info
            }
        else:
            addresses = {literal_address}
    except (OSError, UnicodeError, ValueError):
        return False

    # Reject the hostname if any returned address is non-public, not merely
    # when every answer is private; mixed DNS answers can otherwise bypass the guard.
    return bool(addresses) and all(address.is_global for address in addresses)


async def probe_stream(
    url: str,
    *,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
) -> MetadataProbeResult:
    result = MetadataProbeResult(url=url)

    try:
        parsed = urlparse(url)
    except (TypeError, ValueError):
        result.error = "invalid_url"
        return result
    if parsed.scheme not in {"http", "https"}:
        result.error = "unsupported_scheme"
        return result

    headers = {
        "User-Agent": USER_AGENT,
        "Icy-MetaData": "1",
        "Accept": "*/*",
        "Connection": "close",
    }
    timeout = httpx.Timeout(
        connect=min(timeout_seconds, 5.0),
        read=timeout_seconds,
        write=timeout_seconds,
        pool=timeout_seconds,
    )

    try:
        # Redirects are handled explicitly so every new destination is validated.
        # Do not inherit HTTP proxy settings from the API process environment.
        async with httpx.AsyncClient(
            follow_redirects=False,
            trust_env=False,
            timeout=timeout,
            headers=headers,
        ) as client:
            current_url = url
            for redirect_count in range(MAX_REDIRECTS + 1):
                if not await _is_public_http_url(current_url):
                    result.error = "non_public_target"
                    return result

                async with client.stream("GET", current_url) as response:
                    if response.status_code in REDIRECT_STATUSES:
                        result.http_status = response.status_code
                        result.redirected_url = str(response.url)
                        location = response.headers.get("location")
                        if not location:
                            result.error = "redirect_without_location"
                            return result
                        if redirect_count >= MAX_REDIRECTS:
                            result.error = "too_many_redirects"
                            return result

                        current_url = str(response.url.join(location))
                        continue

                    result.http_status = response.status_code
                    result.content_type = response.headers.get("content-type")
                    result.redirected_url = str(response.url)

                    if response.status_code >= 400:
                        result.error = f"http_{response.status_code}"
                        return result

                    result.ok = True

                    result.icy_metaint = _parse_int(response.headers.get("icy-metaint"))
                    result.icy_name = _clean(response.headers.get("icy-name"))
                    result.icy_genre = _clean(response.headers.get("icy-genre"))
                    result.icy_br = _clean(response.headers.get("icy-br"))
                    result.icy_url = _clean(response.headers.get("icy-url"))

                    if result.icy_metaint is None or result.icy_metaint <= 0:
                        result.metadata_protocol = (
                            "hls" if ".m3u8" in str(response.url).lower() else None
                        )
                        result.error = "no_icy_metaint"
                        return result

                    # The value is supplied by the remote stream. Bound the pre-metadata
                    # read so a malformed server cannot grow _ByteReader's buffer without limit.
                    if result.icy_metaint > MAX_ICY_METAINT_BYTES:
                        result.error = "icy_metaint_too_large"
                        return result

                    result.metadata_protocol = "icy"
                    reader = _ByteReader(response, timeout_seconds=timeout_seconds)

                    audio = await reader.read_exact(result.icy_metaint)
                    if len(audio) < result.icy_metaint:
                        result.error = "stream_ended_before_metadata"
                        return result

                    length_byte = await reader.read_exact(1)
                    if not length_byte:
                        result.error = "no_metadata_length"
                        return result

                    metadata_length = length_byte[0] * 16
                    if metadata_length == 0:
                        result.raw_metadata = None
                        result.error = "empty_icy_metadata"
                        return result

                    metadata = await reader.read_exact(metadata_length)
                    if len(metadata) < metadata_length:
                        result.error = "incomplete_icy_metadata"
                        return result

                    title, raw = _parse_title(metadata)
                    result.stream_title = title
                    result.raw_metadata = raw
                    result.has_track_metadata = bool(title)
                    if not title:
                        result.error = "icy_metadata_without_streamtitle"

                    return result

            result.error = "too_many_redirects"
            return result

    except (asyncio.TimeoutError, httpx.TimeoutException):
        result.error = "timeout"
    except httpx.HTTPError as exc:
        result.error = f"http_error:{type(exc).__name__}"
    except Exception as exc:
        result.error = f"probe_error:{type(exc).__name__}"

    return result

def _parse_int(value: str | None) -> int | None:
    if not value:
        return None
    try:
        return int(value.strip())
    except ValueError:
        return None


async def probe_streams(
    urls: list[str],
    *,
    concurrency: int = 8,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
) -> list[MetadataProbeResult]:
    request_semaphore = asyncio.Semaphore(max(1, concurrency))
    global_semaphore = _global_probe_semaphore()

    async def run(url: str) -> MetadataProbeResult:
        async with request_semaphore:
            async with global_semaphore:
                return await probe_stream(url, timeout_seconds=timeout_seconds)

    return await asyncio.gather(*(run(url) for url in urls))
