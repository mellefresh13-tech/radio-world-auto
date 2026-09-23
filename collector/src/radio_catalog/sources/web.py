from __future__ import annotations

import httpx

from ..discovery import extract_stream_candidates


def discover_from_homepage(homepage: str) -> list[str]:
    response = httpx.get(
        homepage,
        headers={"User-Agent": "RadioWorldAuto/0.1"},
        timeout=20,
        follow_redirects=True,
    )
    response.raise_for_status()
    return extract_stream_candidates(response.text, str(response.url))
