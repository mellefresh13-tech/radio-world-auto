from __future__ import annotations

import threading

import pytest

from radio_api.app import _parse_catalog_refresh_seconds


def test_refresh_interval_accepts_supported_upper_bound() -> None:
    maximum = int(threading.TIMEOUT_MAX)

    assert _parse_catalog_refresh_seconds(str(maximum)) == maximum


@pytest.mark.parametrize(
    "value",
    ["0", "-1", str(int(threading.TIMEOUT_MAX) + 1), "nan", "1.5", "invalid", ""],
)
def test_refresh_interval_rejects_values_outside_supported_range(value: str) -> None:
    with pytest.raises(ValueError, match="supported sleep range"):
        _parse_catalog_refresh_seconds(value)
