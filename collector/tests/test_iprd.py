from __future__ import annotations

import pytest

from radio_catalog.sources import iprd


def test_iprd_filters_non_object_and_unnamed_station_records(monkeypatch) -> None:
    payload = [
        {"id": "valid", "name": "Valid IPRD FM"},
        None,
        {"id": "blank", "name": "   "},
        "not a station object",
        {"id": "missing-name"},
    ]
    monkeypatch.setattr(iprd, "get_json", lambda *args, **kwargs: payload)

    assert iprd.fetch_catalog() == [{"id": "valid", "name": "Valid IPRD FM"}]


@pytest.mark.parametrize("payload", [[], [None, "not a record"], [{"id": "no-name"}], {"wrong": []}])
def test_iprd_rejects_catalog_without_usable_named_records(monkeypatch, payload) -> None:
    monkeypatch.setattr(iprd, "get_json", lambda *args, **kwargs: payload)

    with pytest.raises(ValueError, match="IPRD catalog"):
        iprd.fetch_catalog()


def test_iprd_accepts_station_list_nested_in_object(monkeypatch) -> None:
    payload = {"stations": [{"id": "valid", "name": "Valid IPRD FM"}]}
    monkeypatch.setattr(iprd, "get_json", lambda *args, **kwargs: payload)

    assert iprd.fetch_catalog() == payload["stations"]
