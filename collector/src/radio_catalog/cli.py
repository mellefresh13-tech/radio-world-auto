from __future__ import annotations

import argparse

from .genres import normalize_genres
from .merge import merge_stations
from .normalize import normalize_iprd, normalize_radio_browser
from .report import write_coverage_report
from .snapshot import write_snapshot
from .sources.iprd import fetch_catalog as fetch_iprd
from .sources.radio_browser import fetch_all_stations


def build_snapshot(output: str, report: str) -> None:
    radio_browser = [
        normalize_radio_browser(row)
        for row in fetch_all_stations()
    ]
    iprd = [normalize_iprd(row) for row in fetch_iprd()]

    merged = merge_stations(radio_browser + iprd)

    for station in merged:
        station.genres = normalize_genres(station.genres)

    write_snapshot(merged, output)
    write_coverage_report(merged, report)

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
    parser.add_argument(
        "--report",
        default="data/generated/coverage.json",
    )
    args = parser.parse_args()

    build_snapshot(args.output, args.report)


if __name__ == "__main__":
    main()
