#!/usr/bin/env python3
"""Create a diagnostic report from the published station catalog and live HTTP stream."""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.request
from pathlib import Path

CATALOG_PATH = Path("stations.json")
STREAM_HEADERS = (
    "icy-name", "icy-description", "icy-genre", "icy-url", "icy-br",
    "icy-pub", "icy-metaint", "content-type", "server", "cache-control",
    "expires", "date", "pragma",
)


def display(value):
    if value is None or value == "" or value == []:
        return "не указано"
    if isinstance(value, list):
        return ", ".join(str(item) for item in value) or "не указано"
    return str(value)


def find_station(query: str, stations: list[dict]) -> dict:
    needle = query.strip().casefold()
    exact = [
        station for station in stations
        if needle in {str(station.get("id", "")).casefold(),
                      str(station.get("name", "")).casefold()}
    ]
    matches = exact or [
        station for station in stations
        if needle in str(station.get("name", "")).casefold()
        or needle in str(station.get("id", "")).casefold()
        or any(needle in str(alias).casefold() for alias in station.get("aliases", []))
    ]
    if not matches:
        raise RuntimeError(f"Станция не найдена в опубликованном каталоге: {query}")
    if len(matches) > 1:
        choices = ", ".join(f'{s.get("name")} ({s.get("id")})' for s in matches[:10])
        raise RuntimeError(f"Найдено несколько станций. Уточни название или ID: {choices}")
    return matches[0]


def normalized_streams(station: dict) -> list[dict]:
    result = []
    for index, item in enumerate(station.get("streams") or [], start=1):
        stream = item if isinstance(item, dict) else {"url": item}
        result.append({"index": index, **stream})
    return result


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


def parse_track_title(metadata: str):
    match = re.search(r"""StreamTitle\s*=\s*(['"])(.*?)\1""", metadata, re.I | re.S)
    if not match or not match.group(2).strip():
        return None, None
    raw = match.group(2).strip()
    parts = re.split(r"\s+-\s+|\s+–\s+|\s+—\s+", raw, maxsplit=1)
    if len(parts) == 2:
        return parts[0].strip() or None, parts[1].strip() or None
    return None, raw


def inspect_stream(url: str, timeout_seconds: int) -> dict:
    result = {
        "requested_url": url, "final_url": None, "http_status": None,
        "connect_seconds": None, "headers": {}, "icy_metadata": None,
        "artist": None, "title": None, "error": None,
    }
    request = urllib.request.Request(url, headers={
        "Icy-MetaData": "1",
        "User-Agent": "RadioWorldAuto-LiveMetadataDebugger/2.0",
        "Accept": "*/*",
        "Connection": "close",
    })
    started = time.monotonic()
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except Exception as exc:
        result["connect_seconds"] = round(time.monotonic() - started, 3)
        result["error"] = f"Не удалось открыть поток: {type(exc).__name__}: {exc}"
        return result

    result["connect_seconds"] = round(time.monotonic() - started, 3)
    with response:
        headers = response.headers
        result["http_status"] = response.status
        result["final_url"] = response.geturl()
        result["headers"] = {name: headers.get(name) for name in STREAM_HEADERS if headers.get(name)}
        metaint_value = headers.get("icy-metaint")
        if not metaint_value:
            result["error"] = (
                "Ответ не содержит icy-metaint. ICY-метаданные этим способом не обнаружены; "
                "для HLS/ID3 требуется отдельный анализ сегментов."
            )
            return result
        try:
            metaint = int(metaint_value)
            if not 0 < metaint <= 10_000_000:
                raise ValueError()
        except ValueError:
            result["error"] = f"Некорректный icy-metaint: {metaint_value}"
            return result
        result["metaint"] = metaint

        deadline = time.monotonic() + timeout_seconds
        try:
            while time.monotonic() < deadline:
                read_exact(response, metaint, deadline)
                length_byte = read_exact(response, 1, deadline)[0]
                metadata_length = length_byte * 16
                if not metadata_length:
                    continue
                raw_metadata = read_exact(response, metadata_length, deadline)
                metadata = raw_metadata.rstrip(b"\x00").decode("utf-8", errors="replace")
                artist, title = parse_track_title(metadata)
                if metadata.strip():
                    result["icy_metadata"] = metadata
                    result["artist"] = artist
                    result["title"] = title
                    result["metadata_received_after_seconds"] = round(
                        timeout_seconds - max(0, deadline - time.monotonic()), 2
                    )
                    return result
            result["error"] = f"За {timeout_seconds} сек. не получен непустой ICY-блок."
        except Exception as exc:
            result["error"] = f"Ошибка чтения ICY-данных: {type(exc).__name__}: {exc}"
    return result


def render_report(station: dict, streams: list[dict], selected: dict,
                  result: dict, wait_seconds: int) -> str:
    lines = [
        "# Диагностика радиостанции", "",
        "## 1. Карточка станции — данные каталога", "",
        f"- **Название:** {display(station.get('name'))}",
        f"- **ID:** {display(station.get('id'))}",
        f"- **Страна (ISO):** {display(station.get('country'))}",
        f"- **Город:** {display(station.get('city'))}",
        f"- **Языки:** {display(station.get('languages'))}",
        f"- **Жанры:** {display(station.get('genres'))}",
        f"- **Теги:** {display(station.get('tags'))}",
        f"- **Статус каталога:** {display(station.get('status'))}",
        f"- **Псевдонимы:** {display(station.get('aliases'))}",
        f"- **Сайт:** {display(station.get('homepage'))}",
        f"- **Логотип:** {display(station.get('logo'))}", "",
        "> Эти сведения взяты из опубликованного каталога, а не из аудиопотока.", "",
        "## 2. Все потоки станции — данные каталога", "",
    ]
    if not streams:
        lines.append("Потоки не перечислены.")
    for stream in streams:
        lines.extend([
            f"### Поток {stream['index']}",
            f"- URL: {display(stream.get('url'))}",
            f"- Статус каталога: {display(stream.get('status'))}",
            f"- Кодек: {display(stream.get('codec'))}",
            f"- Формат: {display(stream.get('format'))}",
            f"- Протокол: {display(stream.get('protocol'))}",
            f"- Битрейт: {display(stream.get('bitrate_kbps'))} кбит/с",
            f"- HLS: {display(stream.get('is_hls'))}",
            f"- Надёжность: {display(stream.get('reliability'))}",
            f"- Последняя проверка каталога: {display(stream.get('last_checked_at'))}",
            f"- Источник записи: {display(stream.get('source'))}", "",
        ])
    lines.extend([
        "## 3. Проверка выбранного живого потока", "",
        f"- **Выбран:** поток {selected['index']} из {len(streams)}",
        f"- **Запрошенный URL:** {display(result.get('requested_url'))}",
        f"- **Фактический URL после редиректов:** {display(result.get('final_url'))}",
        f"- **HTTP статус:** {display(result.get('http_status'))}",
        f"- **Время установления соединения:** {display(result.get('connect_seconds'))} сек.",
        f"- **Ожидание метаданных:** до {wait_seconds} сек.", "",
        "### Заголовки HTTP/ICY, полученные от сервера", "",
    ])
    if result.get("headers"):
        lines.extend(f"- {key}: {val}" for key, val in result["headers"].items())
    else:
        lines.append("Сервер не вернул доступных диагностических заголовков.")
    lines.extend([
        "", "### Метаданные внутри аудиопотока", "",
        f"- **Исполнитель:** {display(result.get('artist'))}",
        f"- **Название:** {display(result.get('title'))}",
        f"- **Полный ICY-блок:** {display(result.get('icy_metadata'))}",
        f"- **icy-metaint:** {display(result.get('metaint'))}",
        f"- **Время получения ICY-блока:** {display(result.get('metadata_received_after_seconds'))} сек.",
        f"- **Результат/ошибка:** {display(result.get('error'))}", "",
        "## 4. Источники и ограничения", "",
        f"- **Источник каталожной записи:** {display(station.get('sources'))}",
        "- Страна, город, жанры, логотип и список потоков — сведения каталога.",
        "- Заголовки icy-* и ICY-блок — ответ сервера выбранного аудиопотока.",
        "- Логотип каталога — логотип станции, не обязательно обложка текущего трека.",
        "- Если сервер не поддерживает ICY, это не исключает ID3 внутри HLS/аудиосегментов.",
        "- Отсутствующие поля обозначены как «не указано»; значения не додумываются.", "",
    ])
    if station.get("logo"):
        lines.extend([f"![Логотип станции]({station['logo']})", ""])
    return "\n".join(lines)


def main() -> int:
    query = os.environ.get("STATION", "").strip()
    wait_seconds = int(os.environ.get("WAIT_SECONDS", "45"))
    stream_index = int(os.environ.get("STREAM_INDEX", "1"))
    if not query:
        raise RuntimeError("Не задано название станции или ID")
    if not 20 <= wait_seconds <= 180:
        raise RuntimeError("Время ожидания должно быть от 20 до 180 секунд")

    stations = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    station = find_station(query, stations)
    streams = normalized_streams(station)
    if not streams:
        raise RuntimeError("В каталоге у станции нет потоков")
    selected = next((s for s in streams if s["index"] == stream_index), None)
    if selected is None:
        raise RuntimeError(f"У станции нет потока с номером {stream_index}")
    url = selected.get("url")
    if not isinstance(url, str) or not url.startswith(("http://", "https://")):
        raise RuntimeError("Выбранный поток не содержит корректный HTTP(S) URL")

    print(f"Диагностика: {station.get('name')} ({station.get('id')})")
    print(f"Потоков в каталоге: {len(streams)}; проверяется поток №{stream_index}")
    result = inspect_stream(url, wait_seconds)
    report = render_report(station, streams, selected, result, wait_seconds)
    print(report)
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        Path(summary_path).write_text(report + "\n", encoding="utf-8")
    return 1 if result.get("error") else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"ОШИБКА: {error}", file=sys.stderr)
        summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_path:
            with open(summary_path, "a", encoding="utf-8") as summary:
                summary.write(f"\n## Ошибка диагностики\n\n{error}\n")
        raise SystemExit(1)
