from __future__ import annotations

from datetime import datetime, timezone
from math import isfinite
from urllib.parse import urlparse

import pycountry

from .genres import normalize_genres
from .models import SourceRecord, Station, Stream


def resolve_country(value: object | None) -> str:
    if not isinstance(value, str) or not value.strip():
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


def _optional_text(value: object) -> str | None:
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, str):
        clean = value.strip()
    elif isinstance(value, (int, float)):
        clean = str(value)
    else:
        return None
    return clean or None


def _optional_nonnegative_int(value: object) -> int | None:
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value if value >= 0 else None
    if isinstance(value, float):
        if not isfinite(value) or not value.is_integer() or value < 0:
            return None
        return int(value)
    if isinstance(value, str):
        try:
            parsed = int(value.strip())
        except ValueError:
            return None
        return parsed if parsed >= 0 else None
    return None


def _optional_reliability(value: object) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return None
    return parsed if isfinite(parsed) and 0 <= parsed <= 1 else None


def _items(value: object) -> list[object]:
    if isinstance(value, list):
        return value
    if isinstance(value, str):
        return [value]
    return []


def _truthy_hls(value: object) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        return value.strip().casefold() in {"1", "true", "yes", "on"}
    return False


def normalize_radio_browser(row: dict) -> Station:
    name = _optional_text(row.get("name"))
    if not name:
        raise ValueError("Radio Browser station record requires a non-empty name")

    station_id = (
        _optional_text(row.get("stationuuid"))
        or _optional_text(row.get("changeuuid"))
        or name
    )
    url = optional_url(row.get("url_resolved")) or optional_url(row.get("url"))

    streams: list[Stream] = []
    if url:
        streams.append(
            Stream(
                url=url,
                codec=_optional_text(row.get("codec")),
                bitrate_kbps=_optional_nonnegative_int(row.get("bitrate")),
                is_hls=_truthy_hls(row.get("hls")),
                status="candidate",
                source="radio-browser",
            )
        )

    tags = split_values(row.get("tags"))

    return Station(
        id=f"rb:{station_id}",
        name=name,
        country=resolve_country(row.get("countrycode")),
        city=_optional_text(row.get("state")),
        languages=split_values(row.get("languagecodes")),
        genres=normalize_genres(tags),
        tags=tags,
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
    name = _optional_text(row.get("name"))
    if not name:
        raise ValueError("IPRD station record requires a non-empty name")
    station_id = _optional_text(row.get("id")) or name

    raw_streams = row.get("streams")
    streams: list[Stream] = []
    if isinstance(raw_streams, list):
        for item in raw_streams:
            if not isinstance(item, dict):
                continue
            url = optional_url(item.get("url"))
            if not url:
                continue

            stream_format = _optional_text(item.get("format"))
            streams.append(
                Stream(
                    url=url,
                    format=stream_format,
                    bitrate_kbps=_optional_nonnegative_int(item.get("bitrate")),
                    reliability=_optional_reliability(item.get("reliability")),
                    is_hls=(stream_format or "").casefold() == "hls"
                    or ".m3u8" in url.casefold(),
                    status="candidate",
                    source="iprd",
                )
            )

    raw_genres = _items(row.get("genres"))
    raw_tags = _items(row.get("tags"))
    genre_values = [str(value) for value in raw_genres + raw_tags if value is not None]
    tags = [
        text.casefold()
        for value in raw_tags
        if (text := _optional_text(value)) is not None
    ]

    return Station(
        id=f"iprd:{station_id}",
        name=name,
        country=resolve_country(row.get("country")),
        languages=split_values(row.get("language")),
        genres=normalize_genres(genre_values),
        tags=tags,
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
    try:
        return (urlparse(url).hostname or "").lower()
    except ValueError:
        return ""
