#!/usr/bin/env python3
"""Protect catalog publication from accidental large station-count regressions."""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path


def main() -> int:
    candidate_path = Path(sys.argv[1])
    previous_path = Path(sys.argv[2])
    allow_shrink = os.environ.get("ALLOW_CATALOG_SHRINK", "").strip().lower() in {"1", "true", "yes"}

    try:
        candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
        candidate_count = candidate.get("station_count") if isinstance(candidate, dict) else None
        if not isinstance(candidate_count, int) or isinstance(candidate_count, bool) or candidate_count <= 0:
            raise ValueError("candidate station_count must be a positive integer")
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        raise SystemExit(f"invalid candidate catalog manifest: {exc}") from exc

    if not previous_path.exists():
        if allow_shrink:
            print("WARNING: shrink guard bypassed; previous catalog manifest is unavailable")
            return 0
        raise SystemExit(
            "catalog shrink guard cannot run: previous catalog manifest is unavailable; "
            "inspect catalog-data or manually dispatch with allow_catalog_shrink=true"
        )

    try:
        previous = json.loads(previous_path.read_text(encoding="utf-8"))
        previous_count = previous.get("station_count") if isinstance(previous, dict) else None
        if not isinstance(previous_count, int) or isinstance(previous_count, bool) or previous_count <= 0:
            raise ValueError("previous station_count must be a positive integer")
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        if allow_shrink:
            print(f"WARNING: shrink guard bypassed; previous manifest is invalid: {exc}")
            return 0
        raise SystemExit(f"catalog shrink guard cannot use previous manifest: {exc}") from exc

    minimum_allowed = previous_count * 0.75
    if candidate_count < minimum_allowed and not allow_shrink:
        raise SystemExit(
            "catalog shrink guard failed: "
            f"candidate has {candidate_count} stations; previous catalog has {previous_count}; "
            "decrease exceeds 25%. For an intentional shrink, manually dispatch with "
            "allow_catalog_shrink=true after reviewing the candidate."
        )
    if candidate_count < minimum_allowed:
        print(
            "WARNING: explicit shrink-guard bypass accepted: "
            f"{previous_count} -> {candidate_count} stations"
        )
    else:
        print(f"catalog shrink guard passed: {previous_count} -> {candidate_count} stations")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
