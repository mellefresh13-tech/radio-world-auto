from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import urlparse

import httpx

from .discovery import looks_like_stream_content

AUDIO_MAGIC = (
    b"ID3",
    b"\xff\xfb",
    b"\xff\xf3",
    b"\xff\xf2",
    b"OggS",
)


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


def check_stream(url: str, timeout: float = 12.0) -> StreamCheckResult:
    try:
        with httpx.stream(
            "GET",
            url,
            headers={
                "User-Agent": "RadioWorldAutoCatalog/0.1",
                "Icy-MetaData": "1",
                "Accept": "audio/*,application/vnd.apple.mpegurl,*/*;q=0.5",
            },
            timeout=timeout,
            follow_redirects=True,
        ) as response:
            response.raise_for_status()

            content_type = response.headers.get("content-type")
            received = 0
            prefix = bytearray()

            for chunk in response.iter_bytes():
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

            hls = _looks_like_hls(content_type, str(response.url), bytes(prefix))

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

            parsed = urlparse(str(response.url))
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

    except (httpx.HTTPError, OSError) as exc:
        return StreamCheckResult(
            status="offline",
            content_type=None,
            bytes_received=0,
            is_hls=False,
            error=str(exc),
        )
