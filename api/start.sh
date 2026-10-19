#!/bin/sh
set -eu

export PYTHONPATH="${PYTHONPATH:+$PYTHONPATH:}$PWD/src"

python - <<'PY'
import os
from pathlib import Path
from radio_api.catalog_sync import download_catalog

url = os.environ.get(
    "CATALOG_DB_URL",
    "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/radio.db",
)
target = Path(os.environ.get("RADIO_DB_PATH", "data/radio.db"))

download_catalog(url, target)
print(f"catalog ready: {target}")
PY

exec python -m uvicorn radio_api.app:app --host 0.0.0.0 --port "${PORT:-8000}"
