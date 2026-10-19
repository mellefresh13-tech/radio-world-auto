from __future__ import annotations

import os
import threading
import time
import urllib.request
from pathlib import Path


def download_catalog(url: str, target: str | Path) -> None:
    destination = Path(target)
    destination.parent.mkdir(parents=True, exist_ok=True)

    temporary = destination.with_name(destination.name + ".tmp")

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "radio-world-auto/1.0"},
    )

    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            with temporary.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)

        if temporary.stat().st_size < 4096:
            raise RuntimeError("downloaded catalog is unexpectedly small")

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
