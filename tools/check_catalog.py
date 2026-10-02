#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from typing import Any

CATALOG_URL = "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/stations.json"
MANIFEST_URL = "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-manifest.json"


def download_json(url: str) -> Any:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "RadioWorldAuto-CatalogHealth/1.0", "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def searchable(station: dict[str, Any]) -> str:
    values = [
        station.get("id", ""),
        station.get("name", ""),
        station.get("country", ""),
        station.get("city", ""),
        station.get("homepage", ""),
        *station.get("languages", []),
        *station.get("genres", []),
        *station.get("aliases", []),
    ]
    return " ".join(str(value) for value in values if value).casefold()


def matches(station: dict[str, Any], query: str) -> bool:
    return query.casefold() in searchable(station)


def check_stream(url: str) -> tuple[str, str]:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "RadioWorldAuto-CatalogHealth/1.0",
            "Accept": "*/*",
            "Range": "bytes=0-4095",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            return "online", str(response.status)
    except urllib.error.HTTPError as exc:
        if exc.code in (206, 200, 301, 302, 307, 308, 416):
            return "online", str(exc.code)
        return "offline", f"HTTP {exc.code}"
    except Exception as exc:
        return "offline", type(exc).__name__


def main() -> int:
    parser = argparse.ArgumentParser(description="Check the published Radio World Auto catalog.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--query", help="case-insensitive substring across id/name/aliases and basic metadata")
    group.add_argument("--id", help="exact station id")
    parser.add_argument("--check-streams", action="store_true", help="also probe each matching stream URL")
    args = parser.parse_args()

    try:
        manifest = download_json(MANIFEST_URL)
        stations = download_json(CATALOG_URL)
    except Exception as exc:
        print(f"ERROR: unable to download published catalog: {exc}", file=sys.stderr)
        return 2

    if not isinstance(stations, list):
        print("ERROR: stations.json is not a JSON array", file=sys.stderr)
        return 2

    station_count = manifest.get("station_count")
    if isinstance(station_count, int) and station_count != len(stations):
        print(
            f"ERROR: manifest station_count={station_count}, actual={len(stations)}",
            file=sys.stderr,
        )
        return 2

    if args.id:
        found = [station for station in stations if station.get("id") == args.id]
    else:
        found = [station for station in stations if matches(station, args.query)]

    print(f"catalog_version={manifest.get('version')}")
    print(f"station_count={len(stations)}")
    print(f"matches={len(found)}")

    for index, station in enumerate(found, start=1):
        print(f"\n[{index}] {station.get('name')} | id={station.get('id')}")
        print(
            f"country={station.get('country')} city={station.get('city')} "
            f"aliases={station.get('aliases', [])}"
        )
        print(f"homepage={station.get('homepage')}")
        for stream in station.get("streams", []):
            line = (
                f"  stream={stream.get('url')} "
                f"status={stream.get('status')} "
                f"codec={stream.get('codec')} "
                f"bitrate={stream.get('bitrate_kbps')} "
                f"last_checked={stream.get('last_checked_at')}"
            )
            if args.check_streams:
                health, detail = check_stream(str(stream.get("url", "")))
                line += f" probe={health}({detail})"
            print(line)

    return 0 if found else 1


if __name__ == "__main__":
    raise SystemExit(main())
