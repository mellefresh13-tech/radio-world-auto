from __future__ import annotations

import json
import re
from pathlib import Path

from .models import Station

_CURATED_PATH = Path(__file__).with_name("sources").joinpath("curated.json")
_GENERIC_CURATED_TOKENS = {
    "radio", "rádio", "радио", "радыё", "fm", "am",
    "station", "stereo", "official", "live", "online",
}
_FREQUENCY_RE = re.compile(
    r"(?i)\b(?:8[7-9]|9\d|10\d|11\d)(?:[.,]\d{1,2})?\s*(?:fm|mhz)\b"
    r"|\b(?:fm|mhz)\s*(?:8[7-9]|9\d|10\d|11\d)(?:[.,]\d{1,2})?\b"
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
            text = _norm(str(raw))
            for token in re.findall(r"[a-zа-я0-9]+", text, flags=re.IGNORECASE):
                if token in _GENERIC_CURATED_TOKENS:
                    continue
                if len(token) >= 4 or (token.isalpha() and len(token) == 3 and token.upper() == token):
                    anchors.add(token)
    return tuple(sorted(anchors, key=len, reverse=True))


_CURATED_ANCHORS = _curated_anchors()


def is_curated_family(station: Station) -> bool:
    if any(source.provider == "curated" for source in station.sources):
        return True
    name = _norm(station.name)
    return any(anchor in name for anchor in _CURATED_ANCHORS)


def has_fm_evidence(station: Station) -> bool:
    parts = [
        station.name,
        *(station.aliases or []),
        *(station.tags or []),
        station.homepage or "",
    ]
    text = " ".join(parts)
    if _FREQUENCY_RE.search(text) or _FM_TOKEN_RE.search(text):
        return True

    for source in station.sources:
        source_url = source.source_url or ""
        if _FREQUENCY_RE.search(source_url) or _FM_TOKEN_RE.search(source_url):
            return True

    for stream in station.streams:
        if _FREQUENCY_RE.search(stream.url) or _FM_TOKEN_RE.search(stream.url):
            return True

    return False


def filter_stations(stations: list[Station]) -> tuple[list[Station], dict[str, int]]:
    kept: list[Station] = []
    stats = {
        "input": len(stations),
        "kept": 0,
        "kept_curated": 0,
        "kept_fm_evidence": 0,
        "removed_no_fm_evidence": 0,
    }

    for station in stations:
        curated = is_curated_family(station)
        fm = has_fm_evidence(station)
        if not (curated or fm):
            stats["removed_no_fm_evidence"] += 1
            continue

        kept.append(station)
        stats["kept"] += 1
        if curated:
            stats["kept_curated"] += 1
        if fm:
            stats["kept_fm_evidence"] += 1

    return kept, stats
