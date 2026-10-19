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

    usable = [
        item
        for item in stations
        if isinstance(item, dict)
        and isinstance(item.get("name"), str)
        and item["name"].strip()
    ]
    skipped = len(stations) - len(usable)
    if skipped:
        print(f"IPRD catalog: skipped {skipped} malformed or unnamed station records")
    if not usable:
        raise ValueError("IPRD catalog contains no usable named station records")

    return usable
