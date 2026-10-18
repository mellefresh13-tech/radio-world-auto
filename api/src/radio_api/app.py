from __future__ import annotations

from collections.abc import Iterator
import json
import os
from pathlib import Path
import threading

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.gzip import GZipMiddleware

from .catalog_sync import download_catalog, start_background_refresh
from .db import (
    MAX_STATION_OFFSET,
    connect,
    count_online_streams,
    count_stations,
    has_usable_catalog,
    search_stations,
)
from .metadata_probe import probe_streams
from .models import (
    CountryResponse,
    GenreResponse,
    MetadataProbeListResponse,
    StationMetadataResponse,
    StationListResponse,
    StationResponse,
    StreamResponse,
)

DB_PATH = Path(os.getenv("RADIO_DB_PATH", "data/radio.db"))
CATALOG_DB_URL = os.getenv(
    "CATALOG_DB_URL",
    "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/radio.db",
)
def _parse_catalog_refresh_seconds(value: str) -> int:
    try:
        interval = int(value)
    except ValueError as exc:
        raise ValueError(
            "CATALOG_REFRESH_SECONDS must be a positive integer within the supported sleep range"
        ) from exc
    if interval <= 0 or interval > threading.TIMEOUT_MAX:
        raise ValueError(
            "CATALOG_REFRESH_SECONDS must be a positive integer within the supported sleep range"
        )
    return interval


CATALOG_REFRESH_SECONDS = _parse_catalog_refresh_seconds(
    os.getenv("CATALOG_REFRESH_SECONDS", "21600")
)
MAX_METADATA_PROBE_JOBS = 32
MAX_CONCURRENT_METADATA_REQUESTS = 4
_metadata_request_slots = threading.BoundedSemaphore(MAX_CONCURRENT_METADATA_REQUESTS)


def _metadata_probe_request_slot() -> Iterator[None]:
    if not _metadata_request_slots.acquire(blocking=False):
        raise HTTPException(
            status_code=429,
            detail=(
                "Too many metadata diagnostics are running; "
                "retry after an active request completes"
            ),
        )
    try:
        yield
    finally:
        _metadata_request_slots.release()


app = FastAPI(
    title="Radio World Auto API",
    version="0.1.0",
    description="Read-only API for the canonical worldwide radio station catalog.",
)

app.add_middleware(GZipMiddleware, minimum_size=1024)


@app.on_event("startup")
def startup() -> None:
    # Do not initialize an existing file before the download attempt: SQLite
    # schema initialization can fail on a corrupt DB and block atomic recovery.
    had_usable_catalog = has_usable_catalog(DB_PATH)
    try:
        download_catalog(CATALOG_DB_URL, DB_PATH)
    except Exception as exc:
        if not had_usable_catalog:
            raise
        # Keep serving the last known-good catalog if the remote refresh is down.
        print(f"Initial catalog refresh failed; using existing database: {exc}")

    start_background_refresh(
        CATALOG_DB_URL,
        DB_PATH,
        CATALOG_REFRESH_SECONDS,
    )


@app.get("/health")
def health() -> dict[str, int | str]:
    return {
        "status": "ok",
        "stations": count_stations(DB_PATH),
        "online_streams": count_online_streams(DB_PATH),
    }


@app.get("/countries", response_model=list[CountryResponse])
def countries() -> list[CountryResponse]:
    with connect(DB_PATH) as connection:
        rows = connection.execute(
            """
            SELECT s.country AS code, COUNT(DISTINCT s.id) AS station_count
            FROM stations s
            JOIN streams st
              ON st.station_id = s.id AND st.status = 'online'
            WHERE s.status != 'duplicate'
            GROUP BY s.country
            ORDER BY station_count DESC, s.country
            """
        ).fetchall()

    return [
        CountryResponse(code=row["code"], station_count=row["station_count"])
        for row in rows
    ]


@app.get("/genres", response_model=list[GenreResponse])
def genres() -> list[GenreResponse]:
    with connect(DB_PATH) as connection:
        rows = connection.execute(
            """
            SELECT value AS name, COUNT(DISTINCT s.id) AS station_count
            FROM stations s
            JOIN streams st
              ON st.station_id = s.id AND st.status = 'online'
            JOIN json_each(s.genres_json)
            WHERE s.status != 'duplicate'
            GROUP BY value
            ORDER BY station_count DESC, name
            """
        ).fetchall()

    return [
        GenreResponse(name=row["name"], station_count=row["station_count"])
        for row in rows
    ]


def to_station_response(row, streams: list[dict]) -> StationResponse:
    return StationResponse(
        id=row["id"],
        name=row["name"],
        country=row["country"],
        city=row["city"],
        languages=json.loads(row["languages_json"]),
        genres=json.loads(row["genres_json"]),
        homepage=row["homepage"],
        logo=row["logo"],
        status=row["status"],
        has_online_stream=bool(row["has_online_stream"]),
        streams=[StreamResponse(**stream) for stream in streams],
    )



@app.get("/debug/metadata", response_model=MetadataProbeListResponse)
async def debug_metadata(
    q: str | None = Query(default=None, max_length=100),
    country: str | None = Query(default=None, min_length=2, max_length=2),
    genre: str | None = Query(default=None, max_length=50),
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=MAX_STATION_OFFSET),
    streams_per_station: int = Query(default=1, ge=1, le=3),
    timeout: float = Query(default=8.0, ge=2.0, le=15.0),
    _request_slot: None = Depends(_metadata_probe_request_slot),
) -> MetadataProbeListResponse:
    """Probe real online streams and expose the metadata they actually send."""
    rows = search_stations(
        DB_PATH,
        query=q,
        country=country,
        genre=genre,
        limit=limit,
        offset=offset,
    )

    stations_payload: list[dict] = []
    probe_jobs: list[tuple[int, str]] = []

    with connect(DB_PATH) as connection:
        for index, row in enumerate(rows):
            stream_rows = connection.execute(
                """
                SELECT url
                FROM streams
                WHERE station_id = ? AND status = 'online'
                ORDER BY COALESCE(bitrate_kbps, 0) DESC, id
                LIMIT ?
                """,
                (row["id"], streams_per_station),
            ).fetchall()

            stations_payload.append(
                {
                    "id": row["id"],
                    "name": row["name"],
                    "country": row["country"],
                    "city": row["city"],
                    "homepage": row["homepage"],
                    "streams": [],
                }
            )

            for stream_row in stream_rows:
                probe_jobs.append((index, stream_row["url"]))

    if len(probe_jobs) > MAX_METADATA_PROBE_JOBS:
        raise HTTPException(
            status_code=422,
            detail=(
                f"At most {MAX_METADATA_PROBE_JOBS} stream URLs can be probed "
                "per request; reduce limit or streams_per_station"
            ),
        )

    if probe_jobs:
        # probe_streams also shares a per-process concurrency limit across requests.
        probe_results = await probe_streams(
            [url for _, url in probe_jobs],
            concurrency=8,
            timeout_seconds=timeout,
        )
    else:
        probe_results = []

    for (station_index, _), probe_result in zip(probe_jobs, probe_results):
        stations_payload[station_index]["streams"].append(probe_result.as_dict())

    station_results = [
        StationMetadataResponse(**station)
        for station in stations_payload
    ]

    return MetadataProbeListResponse(
        stations=station_results,
        total=len(station_results),
        probed_stations=len(station_results),
        probed_streams=len(probe_jobs),
    )


@app.get("/stations", response_model=StationListResponse)
def stations(
    q: str | None = Query(default=None, max_length=100),
    country: str | None = Query(default=None, min_length=2, max_length=2),
    genre: str | None = Query(default=None, max_length=50),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0, le=MAX_STATION_OFFSET),
) -> StationListResponse:
    rows = search_stations(
        DB_PATH,
        query=q,
        country=country,
        genre=genre,
        limit=limit,
        offset=offset,
    )

    count_clauses = [
        "s.status != 'duplicate'",
    ]
    count_params: list[object] = []

    if q:
        count_clauses.append(
            "(CASEFOLD(s.name) LIKE ? OR CASEFOLD(s.city) LIKE ? "
            "OR CASEFOLD(s.country) LIKE ? OR EXISTS ("
            "SELECT 1 FROM json_each(s.genres_json) WHERE CASEFOLD(value) LIKE ?))"
        )
        needle = f"%{q.casefold()}%"
        count_params.extend([needle, needle, needle, needle])

    if country:
        count_clauses.append("s.country = ?")
        count_params.append(country.upper())

    if genre:
        count_clauses.append(
            "EXISTS (SELECT 1 FROM json_each(s.genres_json) WHERE CASEFOLD(value) = CASEFOLD(?))"
        )
        count_params.append(genre)

    with connect(DB_PATH) as connection:
        total = int(
            connection.execute(
                """
                SELECT COUNT(*)
                FROM stations s
                JOIN (
                    SELECT DISTINCT station_id
                    FROM streams
                    WHERE status = 'online'
                ) playable
                  ON playable.station_id = s.id
                WHERE """
                + " AND ".join(count_clauses),
                count_params,
            ).fetchone()[0]
        )

        result: list[StationResponse] = []

        for row in rows:
            stream_rows = connection.execute(
                """
                SELECT url, protocol, format, codec, bitrate_kbps,
                       reliability, is_hls, status, last_checked_at, source
                FROM streams
                WHERE station_id = ? AND status = 'online'
                ORDER BY COALESCE(bitrate_kbps, 0) DESC, id
                """,
                (row["id"],),
            ).fetchall()

            result.append(
                to_station_response(row, [dict(stream) for stream in stream_rows])
            )

    return StationListResponse(
        stations=result,
        total=total,
        limit=limit,
        offset=offset,
    )


@app.get("/stations/{station_id}", response_model=StationResponse)
def station(station_id: str) -> StationResponse:
    with connect(DB_PATH) as connection:
        row = connection.execute(
            """
            SELECT s.*,
                   EXISTS(
                       SELECT 1 FROM streams st
                       WHERE st.station_id = s.id AND st.status = 'online'
                   ) AS has_online_stream
            FROM stations s
            WHERE s.id = ?
            """,
            (station_id,),
        ).fetchone()

        if row is None:
            raise HTTPException(status_code=404, detail="Station not found")

        stream_rows = connection.execute(
            """
            SELECT url, protocol, format, codec, bitrate_kbps,
                   reliability, is_hls, status, last_checked_at, source
            FROM streams
            WHERE station_id = ? AND status = 'online'
            ORDER BY COALESCE(bitrate_kbps, 0) DESC, id
            """,
            (station_id,),
        ).fetchall()

    return to_station_response(row, [dict(stream) for stream in stream_rows])
