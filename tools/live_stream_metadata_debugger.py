#!/usr/bin/env python3
"""Read ICY metadata directly from a live radio audio stream."""

from __future__ import annotations

import json
import os
import re
import socket
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

CATALOG_PATH = Path("stations.json")


def find_station(query: str, stations: list[dict]) -> dict:
    needle = query.strip().casefold()
    exact = [
        station for station in stations
        if needle in {
            str(station.get("id", "")).casefold(),
            str(station.get("name", "")).casefold(),
        }
    ]
    matches = exact or [
        station for station in stations
        if needle in str(station.get("name", "")).casefold()
        or needle in str(station.get("id", "")).casefold()
    ]
    if not matches:
        raise RuntimeError(f"Станция не найдена в опубликованном каталоге: {query}")
    if len(matches) > 1:
        choices = ", ".join(
            f'{item.get("name")} ({item.get("id")})' for item in matches[:10]
        )
        raise RuntimeError(f"Найдено несколько станций. Уточни название или ID: {choices}")
    return matches[0]


def stream_url(station: dict) -> str:
    streams = station.get("streams") or []
    for item in streams:
        url = item.get("url") if isinstance(item, dict) else item
        if isinstance(url, str) and url.startswith(("http://", "https://")):
            return url
    raise RuntimeError("У станции в каталоге нет HTTP(S) аудиопотока")


def read_exact(response, count: int, deadline: float) -> bytes:
    chunks = []
    remaining = count
    while remaining:
        if time.monotonic() >= deadline:
            raise TimeoutError("Истекло время ожидания ICY-метаданных")
        chunk = response.read(remaining)
        if not chunk:
            raise RuntimeError("Поток закрыл соединение до получения метаданных")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def parse_track_title(metadata: str) -> tuple[str | None, str | None]:
    match = re.search(r"""StreamTitle\s*=\s*(['"])(.*?)\1""", metadata, re.I | re.S)
    if not match:
        return None, None
    raw = match.group(2).strip()
    if not raw:
        return None, None
    # Keep parsing identical to the app's TrackMetadataParser.
    parts = re.split(r"\s+-\s+|\s+–\s+|\s+—\s+", raw, maxsplit=1)
    if len(parts) == 2:
        return parts[0].strip() or None, parts[1].strip() or None
    return None, raw


def inspect_stream(url: str, timeout_seconds: int) -> dict:
    request = urllib.request.Request(
        url,
        headers={
            "Icy-MetaData": "1",
            "User-Agent": "RadioWorldAuto-LiveMetadataDebugger/1.0",
            "Accept": "*/*",
            "Connection": "close",
        },
    )
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"Не удалось подключиться к аудиопотоку: {exc}") from exc

    with response:
        headers = response.headers
        metaint_value = headers.get("icy-metaint")
        if not metaint_value:
            content_type = headers.get("Content-Type", "не указан")
            raise RuntimeError(
                "Сервер не объявил icy-metaint — ICY-метаданные в этом HTTP-ответе "
                f"не обнаружены. HTTP {response.status}; Content-Type: {content_type}. "
                "Это не доказывает, что метаданных нет в другом формате (например, ID3/HLS)."
            )
        try:
            metaint = int(metaint_value)
        except ValueError as exc:
            raise RuntimeError(f"Некорректный icy-metaint: {metaint_value}") from exc
        if metaint <= 0 or metaint > 10_000_000:
            raise RuntimeError(f"Небезопасное/некорректное значение icy-metaint: {metaint}")

        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            read_exact(response, metaint, deadline)  # Skip audio frame bytes.
            length_byte = read_exact(response, 1, deadline)[0]
            metadata_length = length_byte * 16
            if metadata_length == 0:
                continue
            raw_metadata = read_exact(response, metadata_length, deadline)
            metadata = raw_metadata.rstrip(b"\\0").decode("utf-8", errors="replace")
            artist, title = parse_track_title(metadata)
            if title:
                return {
                    "metadata": metadata,
                    "raw_title": title if artist is None else f"{artist} - {title}",
                    "artist": artist,
                    "title": title,
                    "metaint": metaint,
                    "content_type": headers.get("Content-Type", "не указан"),
                    "final_url": response.geturl(),
                }
        raise TimeoutError(
            f"За {timeout_seconds} секунд поток не прислал непустой StreamTitle"
        )


def main() -> int:
    query = os.environ.get("STATION", "").strip()
    timeout_seconds = int(os.environ.get("WAIT_SECONDS", "90"))
    if not query:
        raise RuntimeError("Не задано название станции или ID")
    if not 20 <= timeout_seconds <= 180:
        raise RuntimeError("Время ожидания должно быть от 20 до 180 секунд")

    stations = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    station = find_station(query, stations)
    url = stream_url(station)

    print(f"Станция: {station.get('name')} ({station.get('id')})")
    print(f"Поток из каталога: {url}")
    print(f"Ожидание ICY-метаданных: до {timeout_seconds} сек.")
    result = inspect_stream(url, timeout_seconds)

    print("\n--- РЕЗУЛЬТАТ: МЕТАДАННЫЕ ИЗ АУДИОПОТОКА ---")
    print(f"Исполнитель: {result['artist'] or 'не передан отдельно'}")
    print(f"Название: {result['title']}")
    print(f"Исходный StreamTitle: {result['raw_title']}")
    print(f"Полный ICY-блок: {result['metadata']}")
    print(f"icy-metaint: {result['metaint']}")
    print(f"Content-Type: {result['content_type']}")
    print(f"Фактический URL после редиректов: {result['final_url']}")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write("## Результат чтения живого аудиопотока\n\n")
            summary.write(f"- **Станция:** {station.get('name')} ({station.get('id')})\n")
            summary.write(f"- **Исполнитель:** {result['artist'] or 'не передан отдельно'}\n")
            summary.write(f"- **Название:** {result['title']}\n")
            summary.write(f"- **Исходный StreamTitle:** `{result['raw_title']}`\n")
            summary.write(f"- **Время ожидания:** до {timeout_seconds} секунд\n")
            summary.write(f"- **Фактический URL:** {result['final_url']}\n")
            summary.write("\n> Это метаданные, прочитанные из ICY-сегментов самого аудиопотока, "
                          "а не данные Now Playing API.\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"ОШИБКА: {error}", file=sys.stderr)
        summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_path:
            with open(summary_path, "a", encoding="utf-8") as summary:
                summary.write(f"## Ошибка диагностики\n\n{error}\n")
        raise SystemExit(1)
