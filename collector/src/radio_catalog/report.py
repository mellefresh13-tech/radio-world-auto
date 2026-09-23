from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

from .models import Station


def write_coverage_report(
    stations: list[Station],
    path: str | Path,
) -> None:
    total_stations = len(stations)
    stations_with_stream = sum(bool(station.streams) for station in stations)
    total_streams = sum(len(station.streams) for station in stations)
    online_streams = sum(
        stream.status == "online"
        for station in stations
        for stream in station.streams
    )

    country_counts = Counter(station.country for station in stations)
    station_status_counts = Counter(station.status for station in stations)
    stream_status_counts = Counter(
        stream.status
        for station in stations
        for stream in station.streams
    )

    payload = {
        "total_stations": total_stations,
        "stations_with_stream": stations_with_stream,
        "total_streams": total_streams,
        "online_streams": online_streams,
        "country_count_with_stations": len(country_counts),
        "station_status": dict(sorted(station_status_counts.items())),
        "stream_status": dict(sorted(stream_status_counts.items())),
        "countries": [
            {"code": code, "station_count": count}
            for code, count in sorted(
                country_counts.items(),
                key=lambda item: (-item[1], item[0]),
            )
        ],
    }

    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
