from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from .models import Stream
from .verify import check_stream


def verify_streams(
    streams: list[Stream],
    *,
    workers: int = 12,
    timeout: float = 12.0,
) -> list[Stream]:
    if not streams:
        return []

    workers = max(1, min(workers, 32))
    result = list(streams)

    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = {
            executor.submit(check_stream, str(stream.url), timeout): index
            for index, stream in enumerate(streams)
        }

        for future in as_completed(futures):
            index = futures[future]
            checked = future.result()
            result[index] = result[index].model_copy(
                update={
                    "status": checked.status,
                    "is_hls": checked.is_hls,
                }
            )

    return result
