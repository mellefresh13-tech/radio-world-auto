from __future__ import annotations

from ..http import get_json

CATALOG_URL = "https://iprd-org.github.io/iprd/site_data/metadata/catalog.json"


def fetch_catalog() -> list[dict]:
    payload = get_json(CATALOG_URL)

    if isinstance(payload, dict):
        stations = payload.get("stations", [])
    else:
        stations = payload

    if not isinstance(stations, list):
        raise ValueError("IPRD catalog does not contain a station list")

    return stations
