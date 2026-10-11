from radio_catalog.sources import radio_browser


def test_discover_servers_uses_dns_srv_and_deduplicates(monkeypatch) -> None:
    class FakeAnswer:
        def __init__(self, target):
            self.target = target

    monkeypatch.setattr(
        radio_browser.dns.resolver,
        "resolve",
        lambda *args, **kwargs: [
            FakeAnswer("srv1.api.radio-browser.info."),
            FakeAnswer("srv1.api.radio-browser.info."),
            FakeAnswer("unrelated.example."),
        ],
    )
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
            else "unrelated.example",
            [],
            [ip],
        ),
    )
    monkeypatch.setattr(radio_browser.random, "shuffle", lambda items: None)

    result = radio_browser.discover_servers()

    assert result.count("https://srv1.api.radio-browser.info") == 1
    assert result.count("https://nl1.api.radio-browser.info") == 1
    assert "https://de1.api.radio-browser.info" in result
    assert "https://de2.api.radio-browser.info" in result
    assert "https://fi1.api.radio-browser.info" in result
    assert "https://at1.api.radio-browser.info" in result
    assert all("unrelated.example" not in item for item in result)


def test_discover_from_server_list_adds_live_mirrors(monkeypatch) -> None:
    monkeypatch.setattr(radio_browser.random, "shuffle", lambda items: None)
    calls = []

    def fake_get_json(url, **kwargs):
        calls.append(url)
        if "down.api" in url:
            raise RuntimeError("HTTP 503")
        return [
            {"name": "nl1.api.radio-browser.info"},
            {"name": "de2.api.radio-browser.info"},
            {"name": "unrelated.example"},
            {"name": "nl1.api.radio-browser.info"},
        ]

    monkeypatch.setattr(radio_browser, "get_json", fake_get_json)

    result = radio_browser.discover_from_server_list([
        "https://down.api.radio-browser.info",
        "https://seed.api.radio-browser.info",
    ])

    assert calls == [
        "https://down.api.radio-browser.info/json/servers",
        "https://seed.api.radio-browser.info/json/servers",
    ]
    assert result.count("https://nl1.api.radio-browser.info") == 1
    assert result.count("https://de2.api.radio-browser.info") == 1
    assert "https://unrelated.example" not in result


def test_fetch_all_stations_fails_over_to_next_server(monkeypatch) -> None:
    monkeypatch.setattr(
        radio_browser,
        "discover_servers",
        lambda: [
            "https://down.api.radio-browser.info",
            "https://up.api.radio-browser.info",
        ],
    )
    monkeypatch.setattr(
        radio_browser,
        "discover_from_server_list",
        lambda servers: servers,
    )
    calls = []

    def fake_get_json(url, **kwargs):
        calls.append((url, kwargs))
        if "down.api" in url:
            raise RuntimeError("HTTP 503")
        return [{"name": "Demo"}]

    monkeypatch.setattr(radio_browser, "get_json", fake_get_json)

    assert radio_browser.fetch_all_stations(limit=123) == [{"name": "Demo"}]
    assert len(calls) == 2
    assert calls[0][1]["params"]["limit"] == 123
    assert calls[0][1]["retries"] == 1


def test_fetch_all_stations_reports_all_servers_failed(monkeypatch) -> None:
    monkeypatch.setattr(
        radio_browser,
        "discover_servers",
        lambda: ["https://down.api.radio-browser.info"],
    )
    monkeypatch.setattr(
        radio_browser,
        "discover_from_server_list",
        lambda servers: servers,
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


def test_fetch_all_stations_falls_back_to_official_database_dump(monkeypatch) -> None:
    monkeypatch.setattr(
        radio_browser,
        "discover_servers",
        lambda: ["https://down.api.radio-browser.info"],
    )
    monkeypatch.setattr(
        radio_browser,
        "discover_from_server_list",
        lambda servers: servers,
    )
    monkeypatch.setattr(radio_browser, "MIN_DATABASE_DUMP_STATIONS", 2)
    calls = []

    def fake_get_json(url, **kwargs):
        calls.append(url)
        if url == radio_browser.DATABASE_DUMP_URL:
            return [{"name": "Demo One"}, {"name": "Demo Two"}]
        raise RuntimeError("API unavailable")

    monkeypatch.setattr(radio_browser, "get_json", fake_get_json)

    result = radio_browser.fetch_all_stations()

    assert len(result) == 2
    assert calls == [
        "https://down.api.radio-browser.info/json/stations",
        radio_browser.DATABASE_DUMP_URL,
    ]


def test_fetch_all_stations_rejects_too_small_database_dump(monkeypatch) -> None:
    monkeypatch.setattr(
        radio_browser,
        "discover_servers",
        lambda: ["https://down.api.radio-browser.info"],
    )
    monkeypatch.setattr(
        radio_browser,
        "discover_from_server_list",
        lambda servers: servers,
    )
    monkeypatch.setattr(radio_browser, "MIN_DATABASE_DUMP_STATIONS", 2)

    def fake_get_json(url, **kwargs):
        if url == radio_browser.DATABASE_DUMP_URL:
            return [{"name": "Incomplete"}]
        raise RuntimeError("API unavailable")

    monkeypatch.setattr(radio_browser, "get_json", fake_get_json)

    try:
        radio_browser.fetch_all_stations()
    except RuntimeError as exc:
        assert "All Radio Browser sources failed" in str(exc)
        assert "contains only 1 stations" in str(exc)
    else:
        raise AssertionError("Expected undersized dump to be rejected")
