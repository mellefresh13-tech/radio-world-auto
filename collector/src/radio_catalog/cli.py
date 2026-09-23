from __future__ import annotations

import argparse

from .normalize import normalize_iprd, normalize_radio_browser
from .snapshot import write_snapshot
from .sources.iprd import fetch_catalog as fetch_iprd
from .sources.radio_browser import fetch_all_stations


def build_snapshot(output: str) -> None:
    stations = [normalize_radio_browser(row) for row in fetch_all_stations()]
    stations.extend(normalize_iprd(row) for row in fetch_iprd())
    write_snapshot(stations, output)
    print(f"Collected {len(stations)} raw-normalized station records")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        default="data/generated/raw-normalized.json",
    )
    args = parser.parse_args()
    build_snapshot(args.output)


if __name__ == "__main__":
    main()
