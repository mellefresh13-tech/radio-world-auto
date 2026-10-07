from __future__ import annotations

import argparse
from datetime import datetime, timezone

from .filters import filter_stations, limit_stations_per_country
from .genres import normalize_genres
from .merge import canonical_url, merge_stations
from .models import Station
from .normalize import normalize_iprd, normalize_radio_browser
from .quality import apply_station_stream_quality
from .report import write_coverage_report
from .snapshot import write_snapshot
from .sources.curated import fetch_catalog as fetch_curated
from .sources.iprd import fetch_catalog as fetch_iprd
from .sources.radio_browser import fetch_all_stations
from .verify_pool import verify_streams


def verify_catalog(
    stations: list[Station],
    workers: int,
    timeout: float,
    max_streams: int,
) -> None:
    unique_urls: dict[str, list[tuple[int, int]]] = {}

    for station_index, station in enumerate(stations):
        for stream_index, stream in enumerate(station.streams):
            key = canonical_url(str(stream.url))
            unique_urls.setdefault(key, []).append((station_index, stream_index))

    unique_streams = []
    keys = []
    priority = []
    regular = []
    for key, locations in unique_urls.items():
        station_index, stream_index = locations[0]
        stream = stations[station_index].streams[stream_index]
        if stream.source.startswith("curated:"):
            priority.append((key, stream))
        else:
            regular.append((key, stream))

    selected = priority + regular
    selected = selected[: max_streams + len(priority)]

    for key, stream in selected:
        keys.append(key)
        unique_streams.append(stream)

    selected_urls = set(keys)
    checked = verify_streams(
        unique_streams,
        workers=workers,
        timeout=timeout,
    )

    checked_by_url = {
        keys[index]: stream
        for index, stream in enumerate(checked)
    }

    timestamp = datetime.now(timezone.utc)

    for station in stations:
        for index, stream in enumerate(station.streams):
            key = canonical_url(str(stream.url))
            if key not in selected_urls:
                continue
            checked_stream = checked_by_url[key]
            station.streams[index] = checked_stream.model_copy(
                update={"last_checked_at": timestamp}
            )


def build_snapshot(
    output: str,
    report: str,
    *,
    verify: bool,
    workers: int,
    timeout: float,
    station_limit: int,
    max_verify_streams: int,
) -> None:
    radio_browser = [
        normalize_radio_browser(row)
        for row in fetch_all_stations(limit=station_limit)
    ]
    iprd = [normalize_iprd(row) for row in fetch_iprd()]
    curated = fetch_curated()

    merged = merge_stations(radio_browser + iprd + curated)
    merged, filter_stats = filter_stations(merged, require_active=False)

    for station in merged:
        station.genres = normalize_genres(station.genres)

    if verify:
        verify_catalog(
            merged,
            workers=workers,
            timeout=timeout,
            max_streams=max_verify_streams,
        )

    apply_station_stream_quality(merged)

    merged, final_filter_stats = filter_stations(merged, require_active=True)
    before_country_limit = len(merged)
    merged = limit_stations_per_country(merged)
    removed_by_country_limit = before_country_limit - len(merged)

    print(
        "Catalog filter: pre-verify "
        + ", ".join(f"{key}={value}" for key, value in filter_stats.items())
        + "; final "
        + ", ".join(f"{key}={value}" for key, value in final_filter_stats.items())
        + f"; country_limit={MAX_STATIONS_PER_COUNTRY}, "
        + f"removed_by_country_limit={removed_by_country_limit}"
    )

    write_snapshot(merged, output)
    write_coverage_report(merged, report)

    online = sum(
        stream.status == "online"
        for station in merged
        for stream in station.streams
    )

    print(
        f"Imported {len(radio_browser) + len(iprd) + len(curated)} records; "
        f"filtered to {len(merged)} stations; "
        f"removed without FM evidence: {final_filter_stats['removed_no_fm_evidence']}; "
        f"online streams: {online}"
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
    parser.add_argument(
        "--skip-verify",
        action="store_true",
        help="Do not perform network stream verification",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=32,
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=6.0,
    )
    parser.add_argument(
        "--station-limit",
        type=int,
        default=20_000,
        help="Maximum number of Radio Browser stations to import",
    )
    parser.add_argument(
        "--max-verify-streams",
        type=int,
        default=6_000,
        help="Maximum number of unique stream URLs verified per snapshot",
    )

    args = parser.parse_args()

    build_snapshot(
        args.output,
        args.report,
        verify=not args.skip_verify,
        workers=args.workers,
        timeout=args.timeout,
        station_limit=args.station_limit,
        max_verify_streams=args.max_verify_streams,
    )


if __name__ == "__main__":
    main()
