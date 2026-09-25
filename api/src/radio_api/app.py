from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.gzip import GZipMiddleware

from .catalog_sync import download_catalog, start_background_refresh
from .db import connect, count_online_streams, count_stations, initialize, search_stations
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
CATALOG_REFRESH_SECONDS = int(os.getenv("CATALOG_REFRESH_SECONDS", "21600"))

app = FastAPI(
    title="Radio World Auto API",
    version="0.1.0",
    description="Read-only API for the canonical worldwide radio station catalog.",
)

app.add_middleware(GZipMiddleware, minimum_size=1024)


@app.on_event("startup")
def startup() -> None:
    initialize(DB_PATH)
    download_catalog(CATALOG_DB_URL, DB_PATH)
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
    offset: int = Query(default=0, ge=0),
    streams_per_station: int = Query(default=1, ge=1, le=3),
    timeout: float = Query(default=8.0, ge=2.0, le=15.0),
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

    if probe_jobs:
        probe_results = await asyncio.gather(
            *(
                probe_streams([url], concurrency=1, timeout_seconds=timeout)
                for _, url in probe_jobs
            )
        )
    else:
        probe_results = []

    for (station_index, _), probe_result in zip(probe_jobs, probe_results):
        stations_payload[station_index]["streams"].append(
            probe_result[0].as_dict()
        )

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
    offset: int = Query(default=0, ge=0),
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
            "(LOWER(s.name) LIKE ? OR LOWER(s.city) LIKE ? "
            "OR LOWER(s.country) LIKE ? OR EXISTS ("
            "SELECT 1 FROM json_each(s.genres_json) WHERE LOWER(value) LIKE ?))"
        )
        needle = f"%{q.casefold()}%"
        count_params.extend([needle, needle, needle, needle])

    if country:
        count_clauses.append("s.country = ?")
        count_params.append(country.upper())

    if genre:
        count_clauses.append(
            "EXISTS (SELECT 1 FROM json_each(s.genres_json) WHERE LOWER(value) = LOWER(?))"
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
