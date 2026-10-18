from __future__ import annotations

import os
import sqlite3
import threading
import time
import urllib.request
from pathlib import Path

from .db import REQUIRED_COLUMNS


MAX_CATALOG_DOWNLOAD_BYTES = 256 * 1024 * 1024
DOWNLOAD_CHUNK_BYTES = 1024 * 1024


def _validate_catalog_database(path: Path) -> None:
    """Reject a corrupt, non-SQLite, or empty catalog before replacing the live DB."""
    database_uri = f"{path.resolve().as_uri()}?mode=ro"
    try:
        connection = sqlite3.connect(database_uri, uri=True)
        try:
            integrity = connection.execute("PRAGMA quick_check").fetchone()
            if integrity is None or integrity[0] != "ok":
                raise RuntimeError("downloaded catalog failed SQLite integrity check")

            tables = {
                row[0]
                for row in connection.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                )
            }
            if not set(REQUIRED_COLUMNS).issubset(tables):
                raise RuntimeError("downloaded catalog is missing required tables")

            for table, required_columns in REQUIRED_COLUMNS.items():
                actual_columns = {
                    row[1]
                    for row in connection.execute(f"PRAGMA table_info({table})")
                }
                missing_columns = required_columns - actual_columns
                if missing_columns:
                    raise RuntimeError(
                        f"downloaded catalog table {table} is missing required columns: "
                        + ", ".join(sorted(missing_columns))
                    )

            station_count = int(
                connection.execute("SELECT COUNT(*) FROM stations").fetchone()[0]
            )
            if station_count <= 0:
                raise RuntimeError("downloaded catalog contains no stations")
        finally:
            connection.close()
    except sqlite3.DatabaseError as exc:
        raise RuntimeError("downloaded catalog is not a valid SQLite database") from exc


def download_catalog(url: str, target: str | Path) -> None:
    destination = Path(target)
    destination.parent.mkdir(parents=True, exist_ok=True)

    temporary = destination.with_name(destination.name + ".tmp")

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "radio-world-auto/1.0"},
    )

    try:
        downloaded_bytes = 0
        with urllib.request.urlopen(request, timeout=60) as response:
            with temporary.open("wb") as output:
                while True:
                    # Read at most one byte beyond the remaining budget so we
                    # can detect an oversized response without writing it to disk.
                    remaining_budget = MAX_CATALOG_DOWNLOAD_BYTES - downloaded_bytes
                    chunk = response.read(min(DOWNLOAD_CHUNK_BYTES, remaining_budget + 1))
                    if not chunk:
                        break
                    if downloaded_bytes + len(chunk) > MAX_CATALOG_DOWNLOAD_BYTES:
                        raise RuntimeError(
                            "downloaded catalog exceeds maximum allowed size "
                            f"of {MAX_CATALOG_DOWNLOAD_BYTES} bytes"
                        )
                    output.write(chunk)
                    downloaded_bytes += len(chunk)

        if temporary.stat().st_size < 4096:
            raise RuntimeError("downloaded catalog is unexpectedly small")

        _validate_catalog_database(temporary)
        os.replace(temporary, destination)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def start_background_refresh(
    url: str,
    target: str | Path,
    interval_seconds: int,
) -> threading.Thread:
    def refresh_loop() -> None:
        while True:
            time.sleep(interval_seconds)
            try:
                download_catalog(url, target)
                print("catalog refreshed")
            except Exception as exc:
                print(f"catalog refresh failed: {exc}")

    worker = threading.Thread(
        target=refresh_loop,
        name="catalog-refresh",
        daemon=True,
    )
    worker.start()
    return worker
