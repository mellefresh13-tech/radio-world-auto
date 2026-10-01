from __future__ import annotations

from .models import Station


def apply_station_stream_quality(stations: list[Station]) -> None:
    for station in stations:
        online = [stream for stream in station.streams if stream.status == "online"]

        if online:
            station.status = "active"
        elif station.streams:
            station.status = "temporarily_unavailable"
        else:
            station.status = "review_required"
