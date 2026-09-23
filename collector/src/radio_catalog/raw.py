from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable


def write_raw_jsonl(records: Iterable[dict], path: str | Path) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)

    with target.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def read_raw_jsonl(path: str | Path) -> list[dict]:
    target = Path(path)
    if not target.exists():
        return []

    records = []
    with target.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records
