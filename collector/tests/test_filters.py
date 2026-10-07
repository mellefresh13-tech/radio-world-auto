from datetime import datetime, timezone

from radio_catalog.filters import filter_stations, limit_stations_per_country
from radio_catalog.models import SourceRecord, Station, Stream


def station(status: str = "active", curated: bool = False) -> Station:
    sources = [
        SourceRecord(
            provider="curated",
            source_id="test",
            discovered_at=datetime.now(timezone.utc),
        )
    ] if curated else []
    return Station(
        id="x",
        name="Test FM",
        country="BY",
        status=status,
        streams=[
            Stream(
                url="https://example.com/live",
                source="test",
                status="online" if status == "active" else "offline",
            )
        ],
        sources=sources,
    )


def test_require_active_removes_inactive_non_curated_station() -> None:
    kept, stats = filter_stations([station("temporarily_unavailable")], require_active=True)

    assert kept == []
    assert stats["removed_inactive"] == 1


def test_require_active_keeps_curated_station_for_manual_review() -> None:
    kept, stats = filter_stations(
        [station("temporarily_unavailable", curated=True)],
        require_active=True,
    )

    assert len(kept) == 1
    assert stats["kept_offline_curated"] == 1


def test_require_active_keeps_station_with_verified_online_stream() -> None:
    kept, stats = filter_stations([station("active")], require_active=True)

    assert len(kept) == 1
    assert stats["kept"] == 1


def test_limit_stations_per_country_caps_country_and_keeps_curated() -> None:
    stations = [
        Station(
            id=f"rb:{index}",
            name=f"Station {index} FM",
            country="MX",
            streams=[Stream(url=f"https://example.com/{index}", source="test", status="online")],
        )
        for index in range(500)
    ]
    curated_station = Station(
        id="curated:mx-test",
        name="Curated FM",
        country="MX",
        streams=[Stream(url="https://example.com/curated", source="curated:test", status="online")],
        sources=[
            SourceRecord(
                provider="curated",
                source_id="mx-test",
                discovered_at=datetime.now(timezone.utc),
            )
        ],
    )

    limited = limit_stations_per_country(stations + [curated_station])

    assert len(limited) == 500
    assert curated_station in limited
