from __future__ import annotations

import re
from urllib.parse import urljoin, urlparse

STREAM_EXTENSIONS = (".mp3", ".aac", ".aacp", ".ogg", ".opus", ".wav", ".m3u8", ".pls", ".m3u")
STREAM_CONTENT_MARKERS = (
    "audio/mpeg",
    "audio/aac",
    "audio/aacp",
    "audio/ogg",
    "audio/opus",
    "application/vnd.apple.mpegurl",
    "audio/x-mpegurl",
)


def extract_stream_candidates(html: str, base_url: str) -> list[str]:
    candidates: set[str] = set()

    for match in re.findall(r"""https?://[^"'<>\s]+""", html, flags=re.IGNORECASE):
        clean = match.rstrip("),.;'\"")
        lowered = clean.lower()
        if lowered.endswith(STREAM_EXTENSIONS) or any(
            marker in lowered for marker in ("stream", "listen", "radio", "icecast", "shoutcast")
        ):
            candidates.add(clean)

    for match in re.findall(r"""(?:src|href|url|streamUrl|stream_url|playlist)\s*[:=]\s*["']([^"']+)["']""", html, flags=re.IGNORECASE):
        absolute = urljoin(base_url, match)
        lowered = absolute.lower()
        if lowered.endswith(STREAM_EXTENSIONS) or any(
            marker in lowered for marker in ("stream", "listen", "radio", "icecast", "shoutcast")
        ):
            candidates.add(absolute)

    return sorted(candidates)


def looks_like_stream_content(content_type: str | None) -> bool:
    if not content_type:
        return False
    value = content_type.lower().split(";", 1)[0].strip()
    return value in STREAM_CONTENT_MARKERS or value.startswith("audio/")


def stream_host(url: str) -> str:
    return (urlparse(url).hostname or "").lower()
