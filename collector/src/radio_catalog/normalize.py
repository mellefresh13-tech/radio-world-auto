from __future__ import annotations

from datetime import datetime, timezone
from urllib.parse import urlparse

import pycountry

from .genres import normalize_genres
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


def optional_url(value: object) -> str | None:
    if value is None:
        return None

    clean = str(value).strip()
    if not clean or clean.casefold() in {"null", "none", "n/a", "na"}:
        return None

    return clean


def split_values(value: str | None) -> list[str]:
    return [
        item.strip().lower()
        for item in str(value or "").replace(";", ",").split(",")
        if item.strip()
    ]


def normalize_radio_browser(row: dict) -> Station:
    station_id = row.get("stationuuid") or row.get("changeuuid") or row["name"]
    url = optional_url(row.get("url_resolved")) or optional_url(row.get("url"))

    streams: list[Stream] = []
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

    tags = split_values(row.get("tags"))

    return Station(
        id=f"rb:{station_id}",
        name=str(row.get("name") or "").strip(),
        country=resolve_country(row.get("countrycode")),
        city=row.get("state") or None,
        languages=split_values(row.get("languagecodes")),
        genres=normalize_genres(tags),
        homepage=optional_url(row.get("homepage")),
        logo=optional_url(row.get("favicon")),
        streams=streams,
        sources=[
            SourceRecord(
                provider="radio-browser",
                source_id=str(station_id),
                source_url=optional_url(row.get("homepage")),
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
                reliability=item.get("reliability"),
                is_hls=str(item.get("format") or "").casefold() == "hls"
                or ".m3u8" in str(url).casefold(),
                status="candidate",
                source="iprd",
            )
        )

    return Station(
        id=f"iprd:{station_id}",
        name=str(row.get("name") or "").strip(),
        country=resolve_country(row.get("country")),
        languages=split_values(row.get("language")),
        genres=normalize_genres(
            [str(value) for value in (row.get("genres") or [])]
            + [str(value) for value in (row.get("tags") or [])]
        ),
        homepage=optional_url(row.get("website")),
        logo=optional_url(row.get("logo")),
        streams=streams,
        sources=[
            SourceRecord(
                provider="iprd",
                source_id=station_id,
                source_url=optional_url(row.get("website")),
                discovered_at=datetime.now(timezone.utc),
            )
        ],
    )


def stream_domain(url: str) -> str:
    return (urlparse(url).hostname or "").lower()
