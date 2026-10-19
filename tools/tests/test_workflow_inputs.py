from __future__ import annotations

from pathlib import Path
import sys
from types import ModuleType, SimpleNamespace

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

    dependency_step = workflow.split("      - name: Install stream-check dependency", 1)[1]
    dependency_step = dependency_step.split("      - name: Check published catalog", 1)[0]
    assert "if: ${{ inputs.check_streams }}" in dependency_step
    assert 'httpx>=0.27,<1' in dependency_step
    assert 'dnspython>=2.6,<3' in dependency_step


def test_catalog_health_stream_probe_reuses_guarded_collector_verifier(monkeypatch) -> None:
    calls: list[tuple[str, float]] = []

    def fake_safe_probe(url: str, timeout: float):
        calls.append((url, timeout))
        return SimpleNamespace(
            status="offline",
            error="stream URL does not resolve exclusively to public HTTP(S) addresses",
            bytes_received=0,
        )

    # Stub the collector module so the API/catalog test job does not need to
    # install collector-only dependencies just to verify delegation.
    package = ModuleType("radio_catalog")
    package.__path__ = []  # type: ignore[attr-defined]
    verifier = ModuleType("radio_catalog.verify")
    verifier.check_stream = fake_safe_probe  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "radio_catalog", package)
    monkeypatch.setitem(sys.modules, "radio_catalog.verify", verifier)
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
