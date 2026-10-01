from __future__ import annotations

from dataclasses import dataclass

import httpx


@dataclass
class StreamCheckResult:
    status: str
    content_type: str | None
    bytes_received: int
    error: str | None = None


def check_stream(url: str, timeout: float = 10.0) -> StreamCheckResult:
    try:
        with httpx.stream(
            "GET",
            url,
            headers={"User-Agent": "RadioWorldAuto/0.1", "Icy-MetaData": "1"},
            timeout=timeout,
            follow_redirects=True,
        ) as response:
            response.raise_for_status()
            received = 0
            for chunk in response.iter_bytes():
                received += len(chunk)
                if received >= 64 * 1024:
                    break

            content_type = response.headers.get("content-type")

            if received == 0:
                return StreamCheckResult(
                    "invalid", content_type, 0, "stream returned no bytes"
                )

            return StreamCheckResult("online", content_type, received)

    except (httpx.HTTPError, OSError) as exc:
        return StreamCheckResult("offline", None, 0, str(exc))
