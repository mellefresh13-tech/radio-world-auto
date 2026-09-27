from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from ..models import SourceRecord, Station, Stream


CURATED_PATH = Path(__file__).with_name("curated.json")


def fetch_catalog() -> list[Station]:
    records = json.loads(CURATED_PATH.read_text(encoding="utf-8"))
    discovered_at = datetime.now(timezone.utc)
    stations: list[Station] = []

    for record in records:
        streams = [
            Stream(
                url=stream["url"],
                source=f"curated:{record['id']}",
                status="candidate",
            )
            for stream in record.get("streams", [])
        ]
        stations.append(
            Station(
                id=record["id"],
                name=record["name"],
                country=record["country"],
                city=record.get("city"),
                languages=record.get("languages", []),
                genres=record.get("genres", []),
                homepage=record.get("homepage"),
                logo=record.get("logo"),
                aliases=record.get("aliases", []),
                streams=streams,
                sources=[
                    SourceRecord(
                        provider="curated",
                        source_id=record["id"],
                        source_url=record.get("source_url"),
                        discovered_at=discovered_at,
                    )
                ],
            )
        )

    return stations
