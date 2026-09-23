import json

from radio_catalog.models import Station, Stream
from radio_catalog.report import write_coverage_report


def test_coverage_report(tmp_path) -> None:
    stations = [
        Station(
            id="de-1",
            name="Demo",
            country="DE",
            streams=[
                Stream(
                    url="https://example.com/live",
                    source="test",
                    status="online",
                )
            ],
        )
    ]

    output = tmp_path / "coverage.json"
    write_coverage_report(stations, output)

    payload = json.loads(output.read_text(encoding="utf-8"))

    assert payload["total_stations"] == 1
    assert payload["online_streams"] == 1
    assert payload["countries"][0]["code"] == "DE"
