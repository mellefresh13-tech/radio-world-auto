from __future__ import annotations

from ..http import get_json

BASE_URL = "https://de1.api.radio-browser.info"


def fetch_all_stations(limit: int = 100_000) -> list[dict]:
    payload = get_json(
        f"{BASE_URL}/json/stations",
        params={"limit": limit, "hidebroken": "false"},
    )

    if not isinstance(payload, list):
        raise ValueError("Radio Browser did not return a station list")

    return payload
