#!/bin/sh
set -eu

export PYTHONPATH="${PYTHONPATH:+$PYTHONPATH:}$PWD/src"

# Catalog download, validation, fallback to the last known-good DB, and
# background refresh are owned by the FastAPI startup lifecycle.
exec python -m uvicorn radio_api.app:app --host 0.0.0.0 --port "${PORT:-8000}"
