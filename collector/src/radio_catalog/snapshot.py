from __future__ import annotations

import json
from pathlib import Path

from .models import Station


def write_snapshot(stations: list[Station], path: str | Path) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)

    payload = [station.model_dump(mode="json") for station in stations]
    target.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
