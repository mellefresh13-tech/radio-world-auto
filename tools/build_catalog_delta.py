import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURRENT = ROOT / "data/generated/canonical-candidate.json"
MANIFEST = ROOT / "data/generated/catalog-manifest.json"
DELTA = ROOT / "data/generated/catalog-delta.json"
HISTORY = ROOT / "data/generated/catalog-delta-history.json"


VOLATILE_KEYS = {"last_checked_at", "discovered_at"}


def stable_value(value):
    if isinstance(value, dict):
        return {
            key: stable_value(item)
            for key, item in value.items()
            if key not in VOLATILE_KEYS
        }
    if isinstance(value, list):
        return [stable_value(item) for item in value]
    return value


def canonical(value):
    return json.dumps(
        stable_value(value),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def semantic_version(stations):
    normalized = [
        stable_value(stations[station_id])
        for station_id in sorted(stations)
    ]
    return sha256_bytes(canonical(normalized).encode("utf-8"))


def load_previous():
    try:
        raw = subprocess.check_output(
            ["git", "show", "origin/catalog-data:data/stations.json"],
            cwd=ROOT,
            stderr=subprocess.DEVNULL,
        )
        previous = json.loads(raw.decode("utf-8"))
        try:
            manifest_raw = subprocess.check_output(
                ["git", "show", "origin/catalog-data:data/catalog-manifest.json"],
                cwd=ROOT,
                stderr=subprocess.DEVNULL,
            )
            base_version = json.loads(manifest_raw.decode("utf-8")).get("version")
        except Exception:
            base_version = None
        return previous, base_version
    except Exception:
        return [], None


def _is_playable_stream_record(stream):
    if not isinstance(stream, dict):
        return False

    raw_url = stream.get("url")
    url = "" if raw_url is None else str(raw_url)
    raw_status = stream.get("status")
    status = "" if raw_status is None else str(raw_status)

    # Mirror StreamAvailabilityPolicy on Android: blank status or exactly
    # "online" (case-insensitive), with no whitespace trimming for "online".
    return bool(url.strip()) and (
        not status.strip() or status.casefold() == "online"
    )


def playable_station_count(stations):
    """Count stations Android keeps after filtering to playable stream URLs."""
    return sum(
        any(_is_playable_stream_record(stream) for stream in (station.get("streams") or []))
        for station in stations
    )


def index_unique_stations(stations, label="catalog"):
    """Build a station index and reject IDs that make counts/deltas ambiguous."""
    by_id = {}
    for index, station in enumerate(stations):
        if not isinstance(station, dict):
            raise ValueError(f"{label} station at index {index} is not an object")

        raw_id = station.get("id")
        if not isinstance(raw_id, str) or not raw_id.strip() or raw_id != raw_id.strip():
            raise ValueError(f"{label} station at index {index} has an invalid id")
        station_id = raw_id

        if station_id in by_id:
            raise ValueError(f"{label} contains duplicate station id: {station_id}")

        by_id[station_id] = station
    return by_id


def counts(stations):
    countries = {}
    genres = {}
    for station in stations:
        code = str(station.get("country", "")).strip()
        if code:
            countries[code] = countries.get(code, 0) + 1
        for genre in station.get("genres") or []:
            genre = str(genre).strip()
            if genre:
                genres[genre] = genres.get(genre, 0) + 1
    return (
        [{"code": code, "station_count": count} for code, count in sorted(countries.items())],
        [{"name": name, "station_count": count} for name, count in sorted(genres.items(), key=lambda x: (-x[1], x[0].lower()))],
    )


def published_delta_history(current_delta):
    """Keep recent delta payloads in a standalone file that survives catalog branch snapshots."""
    entries = []
    try:
        raw = subprocess.check_output(
            ["git", "show", "origin/catalog-data:data/catalog-delta-history.json"],
            cwd=ROOT, stderr=subprocess.DEVNULL,
        )
        previous_history = json.loads(raw.decode("utf-8"))
        if isinstance(previous_history, list):
            entries = [item for item in previous_history if isinstance(item, dict)]
    except Exception:
        try:
            manifest_raw = subprocess.check_output(
                ["git", "show", "origin/catalog-data:data/catalog-manifest.json"],
                cwd=ROOT, stderr=subprocess.DEVNULL,
            )
            previous_manifest = json.loads(manifest_raw.decode("utf-8"))
            delta_raw = subprocess.check_output(
                ["git", "show", "origin/catalog-data:data/catalog-delta.json"],
                cwd=ROOT, stderr=subprocess.DEVNULL,
            )
            previous_delta = json.loads(delta_raw.decode("utf-8"))
            if (
                previous_manifest.get("version") == previous_delta.get("version")
                and previous_manifest.get("base_version") == previous_delta.get("base_version")
                and previous_delta.get("version") != previous_delta.get("base_version")
            ):
                entries.append({
                    "version": previous_manifest["version"],
                    "base_version": previous_manifest["base_version"],
                    "updated_count": previous_manifest.get("updated_count"),
                    "removed_count": previous_manifest.get("removed_count"),
                    "delta": previous_delta,
                })
        except Exception:
            pass

    current_version = current_delta.get("version")
    current_base_version = current_delta.get("base_version")
    if current_version and current_base_version and current_version != current_base_version:
        entries = [
            item for item in entries
            if item.get("version") != current_version
            and item.get("version") != item.get("base_version")
        ]
        entries.append({
            "version": current_version,
            "base_version": current_base_version,
            "updated_count": len(current_delta.get("updated") or []),
            "removed_count": len(current_delta.get("removed_ids") or []),
            "delta": current_delta,
        })
    entries = entries[-12:]
    while entries and len(json.dumps(entries, ensure_ascii=False, separators=(",", ":")).encode("utf-8")) > 8 * 1024 * 1024:
        entries.pop(0)
    return entries


def main():
    current_bytes = CURRENT.read_bytes()
    current = json.loads(current_bytes.decode("utf-8"))
    if not isinstance(current, list) or not current:
        raise SystemExit("current catalog is empty")

    previous, base_version = load_previous()
    previous_by_id = {
        str(item.get("id")): item
        for item in previous
        if isinstance(item, dict) and item.get("id")
    }
    current_by_id = index_unique_stations(current, label="current catalog")
    version = semantic_version(current_by_id)

    updated = [
        item for station_id, item in current_by_id.items()
        if station_id not in previous_by_id or canonical(item) != canonical(previous_by_id[station_id])
    ]
    removed = [station_id for station_id in previous_by_id if station_id not in current_by_id]
    countries, genres = counts(current)

    delta = {
        "version": version,
        "base_version": base_version,
        "updated": updated,
        "removed_ids": removed,
    }
    manifest = {
        "version": version,
        "base_version": base_version,
        "station_count": len(current),
        "playable_station_count": playable_station_count(current),
        "updated_count": len(updated),
        "removed_count": len(removed),
        "countries": countries,
        "genres": genres,
        "full_url": "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/stations.json",
        "delta_url": "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json",
        "generated_at_epoch_ms": int(datetime.now(timezone.utc).timestamp() * 1000),
        "delta_history_url": "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta-history.json",
    }

    DELTA.write_text(json.dumps(delta, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    HISTORY.write_text(
        json.dumps(published_delta_history(delta), ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"version={version} base_version={base_version} stations={len(current_by_id)} updated={len(updated)} removed={len(removed)}")


if __name__ == "__main__":
    main()
