from __future__ import annotations

import importlib
from pathlib import Path
import sys
from types import SimpleNamespace

from tools import check_catalog

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


def test_catalog_health_query_is_passed_through_environment_not_shell_interpolation() -> None:
    workflow = (
        REPOSITORY_ROOT / ".github" / "workflows" / "catalog-health.yml"
    ).read_text(encoding="utf-8")

    assert "QUERY: ${{ inputs.query }}" in workflow
    assert '--query "$QUERY"' in workflow
    assert '--query "${{ inputs.query }}"' not in workflow

    run_block = workflow.split("      - name: Check published catalog", 1)[1]
    run_script = run_block.split("        run: |", 1)[1]
    assert "${{ inputs.query }}" not in run_script


def test_catalog_health_stream_probe_reuses_guarded_collector_verifier(monkeypatch) -> None:
    collector_source = REPOSITORY_ROOT / "collector" / "src"
    monkeypatch.syspath_prepend(str(collector_source))
    verifier = importlib.import_module("radio_catalog.verify")

    calls: list[tuple[str, float]] = []

    def fake_safe_probe(url: str, timeout: float):
        calls.append((url, timeout))
        return SimpleNamespace(
            status="offline",
            error="stream URL does not resolve exclusively to public HTTP(S) addresses",
            bytes_received=0,
        )

    monkeypatch.setattr(verifier, "check_stream", fake_safe_probe)
    monkeypatch.setattr(
        check_catalog.urllib.request,
        "urlopen",
        lambda *args, **kwargs: (_ for _ in ()).throw(
            AssertionError("untrusted stream URL must not be opened by urllib")
        ),
    )

    result = check_catalog.check_stream("file:///etc/passwd")

    assert calls == [("file:///etc/passwd", 12.0)]
    assert result == (
        "offline",
        "stream URL does not resolve exclusively to public HTTP(S) addresses",
    )
