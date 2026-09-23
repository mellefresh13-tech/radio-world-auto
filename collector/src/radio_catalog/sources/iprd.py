from __future__ import annotations

import httpx

CATALOG_URL = "https://iprd-org.github.io/iprd/site_data/metadata/catalog.json"


def fetch_catalog() -> list[dict]:
    response = httpx.get(
        CATALOG_URL,
        headers={"User-Agent": "RadioWorldAuto/0.1"},
        timeout=60,
        follow_redirects=True,
    )
    response.raise_for_status()

    payload = response.json()
    if isinstance(payload, dict):
        stations = payload.get("stations", [])
    else:
        stations = payload

    if not isinstance(stations, list):
        raise ValueError("IPRD catalog does not contain a station list")

    return stations
