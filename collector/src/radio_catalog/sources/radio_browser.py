from __future__ import annotations

import httpx

BASE_URL = "https://de1.api.radio-browser.info"


def fetch_all_stations(limit: int = 100_000) -> list[dict]:
    response = httpx.get(
        f"{BASE_URL}/json/stations",
        params={"limit": limit, "hidebroken": "false"},
        headers={"User-Agent": "RadioWorldAuto/0.1"},
        timeout=60,
    )
    response.raise_for_status()
    return response.json()
