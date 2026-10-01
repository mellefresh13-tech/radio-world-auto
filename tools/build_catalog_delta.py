import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURRENT = ROOT / "data/generated/canonical-candidate.json"
MANIFEST = ROOT / "data/generated/catalog-manifest.json"
DELTA = ROOT / "data/generated/catalog-delta.json"


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_previous():
    try:
        raw = subprocess.check_output(
            ["git", "show", "origin/catalog-data:data/stations.json"],
            cwd=ROOT,
            stderr=subprocess.DEVNULL,
        )
        return json.loads(raw.decode("utf-8")), sha256_bytes(raw)
    except Exception:
        return [], None


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


def main():
    current_bytes = CURRENT.read_bytes()
    current = json.loads(current_bytes.decode("utf-8"))
    if not isinstance(current, list) or not current:
        raise SystemExit("current catalog is empty")

    previous, base_version = load_previous()
    version = sha256_bytes(current_bytes)
    previous_by_id = {str(item.get("id")): item for item in previous if item.get("id")}
    current_by_id = {str(item.get("id")): item for item in current if item.get("id")}

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
        "station_count": len(current_by_id),
        "updated_count": len(updated),
        "removed_count": len(removed),
        "countries": countries,
        "genres": genres,
        "full_url": "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/stations.json",
        "delta_url": "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json",
    }

    DELTA.write_text(json.dumps(delta, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"version={version} base_version={base_version} stations={len(current_by_id)} updated={len(updated)} removed={len(removed)}")


if __name__ == "__main__":
    main()
