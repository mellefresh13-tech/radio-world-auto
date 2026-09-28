from __future__ import annotations

import re
from urllib.parse import urlsplit

from .models import Station


FM_RE = re.compile(
    r"(?i)"
    r"(?:\b(?:fm|mhz)\b)"
    r"|(?:\b(?:6[5-9]|[7-9]\d|10\d|11\d)(?:[.,]\d{1,2})?\s*(?:fm|mhz)\b)"
)
INTERNET_ONLY_RE = re.compile(
    r"(?i)\b(?:"
    r"internet[- ]?only"
    r"|internet\s+radio"
    r"|online[- ]?only"
    r"|web[- ]?radio"
    r"|webradio"
    r"|net[- ]?radio"
    r"|netradio"
    r"|podcasts?"
    r"|stream(?:ing)?[- ]?only"
    r"|internet\s+station"
    r")\b"
)
TOKEN_RE = re.compile(r"[A-Za-zА-Яа-яЁё0-9]+")
GENERIC_BRAND_TOKENS = {
    "radio", "fm", "am", "the", "and", "for", "of", "live", "music",
    "радио", "радио", "своё", "свое", "новое",
}


def _homepage_domain(station: Station) -> str:
    if not station.homepage:
        return ""
    try:
        return (urlsplit(station.homepage).hostname or "").lower().removeprefix("www.")
    except ValueError:
        return ""


def _text(station: Station) -> str:
    return " ".join(
        [
            station.name,
            *station.aliases,
            *station.genres,
        ]
    ).strip()


def _is_curated(station: Station) -> bool:
    return any(source.provider == "curated" for source in station.sources)


def _brand_tokens(curated: list[Station]) -> set[str]:
    tokens: set[str] = set()
    for station in curated:
        for value in [station.name, *station.aliases]:
            for token in TOKEN_RE.findall(value.casefold()):
                if token in GENERIC_BRAND_TOKENS:
                    continue
                if len(token) >= 4 or (token.isascii() and token.isupper() and len(token) >= 3):
                    tokens.add(token)
    return tokens


def _matches_curated_brand(station: Station, tokens: set[str]) -> bool:
    if not tokens:
        return False
    name_tokens = {
        token
        for token in TOKEN_RE.findall(station.name.casefold())
        if token not in GENERIC_BRAND_TOKENS
    }
    return bool(name_tokens & tokens)


def _fm_score(station: Station) -> int:
    text = _text(station)
    score = 0
    if FM_RE.search(text):
        score += 4
    if station.city:
        score += 1
    if station.homepage:
        score += 1
    if station.country and station.country != "ZZ":
        score += 1
    if re.search(r"(?i)\bradio\b", station.name):
        score += 1
    return score


def filter_catalog(stations: list[Station]) -> tuple[list[Station], dict[str, int]]:
    curated = [station for station in stations if _is_curated(station)]
    brand_tokens = _brand_tokens(curated)

    strong_domains = {
        _homepage_domain(station)
        for station in stations
        if station.status == "active"
        and not _is_curated(station)
        and not INTERNET_ONLY_RE.search(_text(station))
        and _fm_score(station) >= 4
        and _homepage_domain(station)
    }

    kept: list[Station] = []
    stats = {
        "input": len(stations),
        "curated": 0,
        "kept_active": 0,
        "removed_inactive": 0,
        "removed_internet_only": 0,
        "removed_not_fm": 0,
        "kept_brand_family": 0,
        "output": 0,
    }

    for station in stations:
        curated_flag = _is_curated(station)
        if curated_flag:
            kept.append(station)
            stats["curated"] += 1
            continue

        if station.status != "active":
            stats["removed_inactive"] += 1
            continue

        text = _text(station)
        internet_only = bool(INTERNET_ONLY_RE.search(text))
        fm_score = _fm_score(station)

        if _matches_curated_brand(station, brand_tokens):
            kept.append(station)
            stats["kept_brand_family"] += 1
            continue

        if _homepage_domain(station) in strong_domains and not internet_only:
            kept.append(station)
            stats["kept_brand_family"] += 1
            continue

        if internet_only and fm_score < 4:
            stats["removed_internet_only"] += 1
            continue

        if fm_score >= 4 and not internet_only:
            kept.append(station)
            stats["kept_active"] += 1
            continue

        stats["removed_not_fm"] += 1

    stats["output"] = len(kept)
    return kept, stats
