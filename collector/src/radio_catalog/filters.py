from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

from .models import Station

_CURATED_PATH = Path(__file__).with_name("sources").joinpath("curated.json")
MAX_STATIONS_PER_COUNTRY = 500
_GENERIC_CURATED_TOKENS = {
    "radio", "rádio", "радио", "радыё", "fm", "am",
    "station", "stereo", "official", "live", "online",
}
_FREQUENCY_RE = re.compile(
    r"(?i)\b(?:"
    r"(?:6[5-9]|[7-9]\d|1[01]\d)(?:[.,]\d{1,2})?\s*(?:fm|mhz)"
    r"|(?:fm|mhz)\s*(?:6[5-9]|[7-9]\d|1[01]\d)(?:[.,]\d{1,2})?"
    r")\b"
)
_FM_TOKEN_RE = re.compile(r"(?i)(?<![a-zа-я0-9])f\.?m\.?(?![a-zа-я0-9])")


def _norm(value: str) -> str:
    return " ".join(value.casefold().replace("ё", "е").split())


def _curated_anchors() -> tuple[str, ...]:
    try:
        records = json.loads(_CURATED_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return ()

    anchors: set[str] = set()
    for record in records:
        for raw in [record.get("name", ""), *record.get("aliases", [])]:
            for token in re.findall(r"[a-zа-я0-9]+", _norm(str(raw)), flags=re.IGNORECASE):
                if token in _GENERIC_CURATED_TOKENS:
                    continue
                if len(token) >= 4 or (token.isascii() and token.isalpha() and len(token) == 3):
                    anchors.add(token)
    return tuple(sorted(anchors, key=len, reverse=True))


_CURATED_ANCHORS = _curated_anchors()


def is_curated_family(station: Station) -> bool:
    if any(source.provider == "curated" for source in station.sources):
        return True
    name_tokens = set(re.findall(r"[a-zа-я0-9]+", _norm(station.name), flags=re.IGNORECASE))
    return bool(name_tokens & set(_CURATED_ANCHORS))


def has_fm_evidence(station: Station) -> bool:
    parts = [
        station.name,
        *(station.aliases or []),
        *(station.tags or []),
    ]
    text = " ".join(parts)
    return bool(_FREQUENCY_RE.search(text) or _FM_TOKEN_RE.search(text))


def filter_stations(
    stations: list[Station],
    *,
    require_active: bool = False,
) -> tuple[list[Station], dict[str, int]]:
    kept: list[Station] = []
    stats = {
        "input": len(stations),
        "kept": 0,
        "kept_curated": 0,
        "kept_fm_evidence": 0,
        "kept_offline_curated": 0,
        "removed_inactive": 0,
        "removed_no_fm_evidence": 0,
    }

    for station in stations:
        direct_curated = any(source.provider == "curated" for source in station.sources)
        curated_family = is_curated_family(station)

        if require_active and station.status != "active":
            if direct_curated:
                kept.append(station)
                stats["kept_offline_curated"] += 1
            else:
                stats["removed_inactive"] += 1
            continue

        fm = has_fm_evidence(station)
        if not (curated_family or fm):
            stats["removed_no_fm_evidence"] += 1
            continue

        kept.append(station)
        stats["kept"] += 1
        if curated_family:
            stats["kept_curated"] += 1
        if fm:
            stats["kept_fm_evidence"] += 1

    return kept, stats


def limit_stations_per_country(
    stations: list[Station],
    *,
    max_per_country: int = MAX_STATIONS_PER_COUNTRY,
) -> list[Station]:
    if max_per_country < 1:
        raise ValueError("max_per_country must be positive")

    grouped: dict[str, list[Station]] = {}
    for station in stations:
        grouped.setdefault(station.country, []).append(station)

    selected_keys: set[tuple[str, str]] = set()

    for country, country_stations in grouped.items():
        if len(country_stations) <= max_per_country:
            selected_keys.update((country, station.id) for station in country_stations)
            continue

        curated = [
            station
            for station in country_stations
            if any(source.provider == "curated" for source in station.sources)
        ]
        selected = curated[:max_per_country]

        remaining_slots = max_per_country - len(selected)
        if remaining_slots > 0:
            selected_ids = {station.id for station in selected}
            remaining = [
                station
                for station in country_stations
                if station.id not in selected_ids
            ]
            remaining.sort(
                key=lambda station: hashlib.sha256(
                    station.id.encode("utf-8")
                ).hexdigest()
            )
            selected.extend(remaining[:remaining_slots])

        selected_keys.update((country, station.id) for station in selected)

    return [
        station
        for station in stations
        if (station.country, station.id) in selected_keys
    ]
