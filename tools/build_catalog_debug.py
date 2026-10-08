#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import subprocess
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CURRENT = ROOT / "data/generated/canonical-candidate.json"
MANIFEST = ROOT / "data/generated/catalog-manifest.json"
DELTA = ROOT / "data/generated/catalog-delta.json"
OUT = ROOT / "data/generated/debug"
VOLATILE = {"last_checked_at", "discovered_at"}

def load(path: Path, default: Any) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default

def previous(path: str, default: Any) -> Any:
    try:
        raw = subprocess.check_output(["git", "show", f"origin/catalog-data:{path}"], cwd=ROOT, stderr=subprocess.DEVNULL)
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return default

def clean(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: clean(v) for k, v in value.items() if k not in VOLATILE}
    if isinstance(value, list):
        return [clean(v) for v in value]
    return value

def canon(value: Any) -> str:
    return json.dumps(clean(value), ensure_ascii=False, sort_keys=True, separators=(",", ":"))

def by_id(items: list[dict]) -> dict[str, dict]:
    return {str(x["id"]): x for x in items if x.get("id")}

def counts(items: list[dict], field: str = "country") -> Counter:
    result = Counter()
    for item in items:
        if field == "country":
            result[str(item.get("country") or "ZZ")] += 1
        else:
            for value in item.get(field) or []:
                value = str(value).strip()
                if value:
                    result[value] += 1
    return result

def stream_stats(items: list[dict]) -> dict[str, int]:
    streams = [s for x in items for s in x.get("streams") or []]
    return {
        "stations": len(items),
        "stations_with_stream": sum(bool(x.get("streams")) for x in items),
        "total_streams": len(streams),
        "online_streams": sum(s.get("status") == "online" for s in streams),
    }

def sources(items: list[dict]) -> dict[str, int]:
    result = Counter()
    for item in items:
        for src in {str(x.get("provider")) for x in item.get("sources") or [] if x.get("provider")}:
            result[src] += 1
    return dict(result)

def main() -> None:
    current = load(CURRENT, [])
    old = previous("data/stations.json", [])
    manifest = load(MANIFEST, {})
    old_manifest = previous("data/catalog-manifest.json", {})
    old_summary = previous("debug/summary.json", {})
    cur = by_id(current)
    prev = by_id(old)

    added = [cur[k] for k in sorted(cur.keys() - prev.keys())]
    removed = [prev[k] for k in sorted(prev.keys() - cur.keys())]
    changed = []
    for k in sorted(cur.keys() & prev.keys()):
        if canon(cur[k]) != canon(prev[k]):
            before, after = prev[k], cur[k]
            fields = sorted({*before.keys(), *after.keys()} - VOLATILE)
            fields = [f for f in fields if canon(before.get(f)) != canon(after.get(f))]
            changed.append({"id": k, "name": after.get("name", before.get("name")), "changed_fields": fields, "before": before, "after": after})

    cc, pc = counts(current), counts(old)
    ac, rc = counts(added), counts(removed)
    countries = []
    for code in sorted(set(cc) | set(pc), key=lambda c: (-cc[c], c)):
        countries.append({
            "code": code, "before": pc[code], "after": cc[code], "delta": cc[code]-pc[code],
            "added": ac[code], "removed": rc[code], "limit_reached": cc[code] >= 500
        })

    stamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    summary = {
        "generated_at": stamp, "catalog_commit": os.getenv("GITHUB_SHA"),
        "version": manifest.get("version"), "previous_version": manifest.get("base_version"),
        "previous_generated_at": old_summary.get("generated_at"),
        "station_count": len(current), "previous_station_count": len(old),
        "station_delta": len(current) - len(old),
        "added_count": len(added), "removed_count": len(removed), "changed_count": len(changed),
        "country_count": len(cc), "limited_country_count": sum(x["limit_reached"] for x in countries),
        "current": {"streams": stream_stats(current), "genres": dict(counts(current, "genres")), "sources": sources(current)},
        "previous": {"streams": stream_stats(old), "genres": dict(counts(old, "genres")), "sources": sources(old)},
        "countries": countries,
        "old_manifest_station_count": old_manifest.get("station_count")
    }
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "delta.json").write_text(json.dumps({
        "generated_at": stamp, "version": manifest.get("version"), "previous_version": manifest.get("base_version"),
        "added": added, "removed": removed, "changed": changed
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"debug stations={len(current)} previous={len(old)} added={len(added)} removed={len(removed)} changed={len(changed)}")

if __name__ == "__main__":
    main()
