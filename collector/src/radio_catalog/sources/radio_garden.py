from __future__ import annotations

import httpx

BASE_URL = "https://radio.garden"


def search(query: str) -> dict:
    response = httpx.get(
        f"{BASE_URL}/api/search",
        params={"q": query},
        headers={"User-Agent": "RadioWorldAuto/0.1"},
        timeout=30,
        follow_redirects=True,
    )
    response.raise_for_status()
    return response.json()


def listen_url(channel_id: str) -> str:
    return f"{BASE_URL}/api/ara/content/listen/{channel_id}/channel.mp3"
