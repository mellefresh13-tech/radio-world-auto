from __future__ import annotations

from datetime import datetime, timezone
from urllib.parse import urlparse

import pycountry

from .models import SourceRecord, Station, Stream


def resolve_country(value: str | None) -> str:
    if not value:
        return "ZZ"

    candidate = value.strip()
    if len(candidate) == 2:
        match = pycountry.countries.get(alpha_2=candidate.upper())
        if match:
            return match.alpha_2

    if len(candidate) == 3:
        match = pycountry.countries.get(alpha_3=candidate.upper())
        if match:
            return match.alpha_2

    lowered = candidate.casefold()
    for country in pycountry.countries:
        names = {
            country.name.casefold(),
            getattr(country, "official_name", "").casefold(),
            getattr(country, "common_name", "").casefold(),
        }
        if lowered in names:
            return country.alpha_2

    return "ZZ"


def normalize_radio_browser(row: dict) -> Station:
    station_id = row.get("stationuuid") or row.get("changeuuid") or row["name"]

    streams: list[Stream] = []
    url = row.get("url_resolved") or row.get("url")
    if url:
        streams.append(
            Stream(
                url=url,
                codec=row.get("codec") or None,
                bitrate_kbps=row.get("bitrate") or None,
                is_hls=bool(row.get("hls")),
                status="candidate",
                source="radio-browser",
            )
        )

    tags = [
        value.strip().lower()
        for value in str(row.get("tags") or "").split(",")
        if value.strip()
    ]
    languages = [
        value.strip().lower()
        for value in str(row.get("languagecodes") or "").split(",")
        if value.strip()
    ]

    return Station(
        id=f"rb:{station_id}",
        name=str(row.get("name") or "").strip(),
        country=resolve_country(row.get("countrycode")),
        city=row.get("state") or None,
        languages=languages,
        genres=tags,
        homepage=row.get("homepage") or None,
        logo=row.get("favicon") or None,
        streams=streams,
        sources=[
            SourceRecord(
                provider="radio-browser",
                source_id=str(station_id),
                source_url=row.get("homepage") or None,
                discovered_at=datetime.now(timezone.utc),
            )
        ],
    )


def normalize_iprd(row: dict) -> Station:
    station_id = str(row.get("id") or row.get("name") or "unknown")

    streams: list[Stream] = []
    for item in row.get("streams") or []:
        url = item.get("url")
        if not url:
            continue
        streams.append(
            Stream(
                url=url,
                format=item.get("format") or None,
                bitrate_kbps=item.get("bitrate") or None,
                status="candidate",
                source="iprd",
            )
        )

    return Station(
        id=f"iprd:{station_id}",
        name=str(row.get("name") or "").strip(),
        country=resolve_country(row.get("country")),
        languages=[str(row["language"]).lower()] if row.get("language") else [],
        genres=[str(item).lower() for item in (row.get("genres") or [])],
        homepage=row.get("website") or None,
        logo=row.get("logo") or None,
        streams=streams,
        sources=[
            SourceRecord(
                provider="iprd",
                source_id=station_id,
                source_url=row.get("website") or None,
                discovered_at=datetime.now(timezone.utc),
            )
        ],
    )


def stream_domain(url: str) -> str:
    return (urlparse(url).hostname or "").lower()
