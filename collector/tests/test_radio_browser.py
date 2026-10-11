from radio_catalog.sources import radio_browser


def test_discover_servers_uses_dns_and_deduplicates(monkeypatch) -> None:
    monkeypatch.setattr(
        radio_browser.socket,
        "getaddrinfo",
        lambda *args, **kwargs: [
            (None, None, None, None, ("192.0.2.1", 0)),
            (None, None, None, None, ("192.0.2.1", 0)),
            (None, None, None, None, ("192.0.2.2", 0)),
        ],
    )
    monkeypatch.setattr(
        radio_browser.socket,
        "gethostbyaddr",
        lambda ip: (
            "nl1.api.radio-browser.info" if ip == "192.0.2.1"
            else "unrelated.example"
        , [], [ip]),
    )
    monkeypatch.setattr(radio_browser.random, "shuffle", lambda items: None)

    result = radio_browser.discover_servers()

    assert result.count("https://nl1.api.radio-browser.info") == 1
    assert "https://de1.api.radio-browser.info" in result
    assert all("unrelated.example" not in item for item in result)


def test_fetch_all_stations_fails_over_to_next_server(monkeypatch) -> None:
    monkeypatch.setattr(
        radio_browser,
        "discover_servers",
        lambda: [
            "https://down.api.radio-browser.info",
            "https://up.api.radio-browser.info",
        ],
    )
    calls = []

    def fake_get_json(url, **kwargs):
        calls.append(url)
        if "down.api" in url:
            raise RuntimeError("HTTP 503")
        return [{"name": "Demo"}]

    monkeypatch.setattr(radio_browser, "get_json", fake_get_json)

    assert radio_browser.fetch_all_stations(limit=123) == [{"name": "Demo"}]
    assert len(calls) == 2
    assert "limit=123" not in calls[1]


def test_fetch_all_stations_reports_all_servers_failed(monkeypatch) -> None:
    monkeypatch.setattr(
        radio_browser,
        "discover_servers",
        lambda: ["https://down.api.radio-browser.info"],
    )
    monkeypatch.setattr(
        radio_browser,
        "get_json",
        lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("HTTP 503")),
    )

    try:
        radio_browser.fetch_all_stations()
    except RuntimeError as exc:
        assert "All Radio Browser servers failed" in str(exc)
        assert "HTTP 503" in str(exc)
    else:
        raise AssertionError("Expected all-server failure")
