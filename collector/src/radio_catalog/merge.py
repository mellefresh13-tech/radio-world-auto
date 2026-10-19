from __future__ import annotations

from urllib.parse import urlsplit, urlunsplit

from .models import Station, Stream


def canonical_url(value: str) -> str:
    clean = value.strip()
    try:
        parts = urlsplit(clean)
    except ValueError:
        # Source records can contain invalid bracketed IPv6/URL syntax. Keep a
        # deterministic identity so one malformed external URL cannot abort the
        # whole merge; the stream verifier later rejects non-HTTP/public targets.
        return clean.casefold()

    return urlunsplit(
        (
            parts.scheme.lower(),
            parts.netloc.lower(),
            parts.path.rstrip("/"),
            parts.query,
            "",
        )
    )


def station_key(station: Station) -> tuple[str, str, str]:
    homepage = canonical_url(str(station.homepage)) if station.homepage else ""
    name = " ".join(station.name.casefold().split())
    return station.country, name, homepage


def _deduplicate_station_records(station: Station) -> None:
    seen_streams: set[str] = set()
    unique_streams: list[Stream] = []
    for stream in station.streams:
        identity = canonical_url(str(stream.url))
        if identity in seen_streams:
            continue
        seen_streams.add(identity)
        unique_streams.append(stream)
    station.streams = unique_streams

    seen_sources: set[tuple[str, str | None, str | None]] = set()
    unique_sources = []
    for source in station.sources:
        identity = (source.provider, source.source_id, str(source.source_url))
        if identity in seen_sources:
            continue
        seen_sources.add(identity)
        unique_sources.append(source)
    station.sources = unique_sources


def merge_stations(stations: list[Station]) -> list[Station]:
    merged: dict[tuple[str, str, str], Station] = {}
    merged_by_id: dict[str, Station] = {}

    for station in stations:
        key = station_key(station)
        # Stable source IDs are authoritative within a source namespace. Some
        # IPRD records reuse an ID while disagreeing on homepage/logo/genres;
        # station_key alone would retain both and break catalog publication.
        current = merged_by_id.get(station.id) or merged.get(key)

        if current is None:
            _deduplicate_station_records(station)
            merged[key] = station
            merged_by_id[station.id] = station
            continue

        merged_by_id[station.id] = current

        for alias in [station.name, *station.aliases]:
            if alias and alias != current.name and alias not in current.aliases:
                current.aliases.append(alias)

        for value in station.languages:
            if value not in current.languages:
                current.languages.append(value)

        for value in station.genres:
            if value not in current.genres:
                current.genres.append(value)

        for value in station.tags:
            if value not in current.tags:
                current.tags.append(value)

        existing_streams = {
            canonical_url(str(stream.url))
            for stream in current.streams
        }
        for stream in station.streams:
            identity = canonical_url(str(stream.url))
            if identity not in existing_streams:
                current.streams.append(stream)
                existing_streams.add(identity)

        existing_sources = {
            (source.provider, source.source_id, str(source.source_url))
            for source in current.sources
        }
        for source in station.sources:
            identity = (source.provider, source.source_id, str(source.source_url))
            if identity not in existing_sources:
                current.sources.append(source)
                existing_sources.add(identity)

    return list(merged.values())
