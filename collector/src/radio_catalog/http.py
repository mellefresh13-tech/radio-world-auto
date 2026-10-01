from __future__ import annotations

import time

import httpx


DEFAULT_HEADERS = {
    "User-Agent": "RadioWorldAutoCatalog/0.1 (+https://github.com/mellefresh13-tech/radio-world-auto)"
}


def get_json(
    url: str,
    *,
    params: dict | None = None,
    timeout: float = 60.0,
    retries: int = 3,
) -> object:
    last_error: Exception | None = None

    for attempt in range(retries):
        try:
            response = httpx.get(
                url,
                params=params,
                headers=DEFAULT_HEADERS,
                timeout=timeout,
                follow_redirects=True,
            )
            response.raise_for_status()
            return response.json()
        except (httpx.HTTPError, ValueError) as exc:
            last_error = exc
            if attempt + 1 < retries:
                time.sleep(1.5 * (attempt + 1))

    raise RuntimeError(f"GET JSON failed: {url}") from last_error
