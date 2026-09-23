from __future__ import annotations

import argparse

from .merge import merge_stations
from .normalize import normalize_iprd, normalize_radio_browser
from .snapshot import write_snapshot
from .sources.iprd import fetch_catalog as fetch_iprd
from .sources.radio_browser import fetch_all_stations


def build_snapshot(output: str) -> None:
    radio_browser = [
        normalize_radio_browser(row)
        for row in fetch_all_stations()
    ]
    iprd = [normalize_iprd(row) for row in fetch_iprd()]

    merged = merge_stations(radio_browser + iprd)
    write_snapshot(merged, output)

    print(
        f"Imported {len(radio_browser) + len(iprd)} records; "
        f"merged into {len(merged)} stations"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        default="data/generated/canonical-candidate.json",
    )
    args = parser.parse_args()
    build_snapshot(args.output)


if __name__ == "__main__":
    main()
