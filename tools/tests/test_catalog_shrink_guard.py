import json
import os
import subprocess
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
GUARD = REPOSITORY_ROOT / "tools" / "check_catalog_shrink.py"


def _manifest(path: Path, count: int) -> None:
    path.write_text(json.dumps({"station_count": count}), encoding="utf-8")


def _run(candidate: Path, previous: Path, *, bypass: bool = False) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["ALLOW_CATALOG_SHRINK"] = "true" if bypass else "false"
    return subprocess.run(
        [sys.executable, str(GUARD), str(candidate), str(previous)],
        capture_output=True,
        text=True,
        env=env,
        check=False,
    )


def test_catalog_shrink_guard_accepts_small_changes(tmp_path: Path) -> None:
    candidate, previous = tmp_path / "candidate.json", tmp_path / "previous.json"
    _manifest(candidate, 8_000)
    _manifest(previous, 10_000)
    result = _run(candidate, previous)
    assert result.returncode == 0
    assert "guard passed" in result.stdout


def test_catalog_shrink_guard_rejects_drop_over_25_percent(tmp_path: Path) -> None:
    candidate, previous = tmp_path / "candidate.json", tmp_path / "previous.json"
    _manifest(candidate, 7_499)
    _manifest(previous, 10_000)
    result = _run(candidate, previous)
    assert result.returncode != 0
    assert "decrease exceeds 25%" in result.stderr


def test_catalog_shrink_guard_allows_explicit_manual_bypass(tmp_path: Path) -> None:
    candidate, previous = tmp_path / "candidate.json", tmp_path / "previous.json"
    _manifest(candidate, 5_000)
    _manifest(previous, 10_000)
    result = _run(candidate, previous, bypass=True)
    assert result.returncode == 0
    assert "explicit shrink-guard bypass" in result.stdout


def test_catalog_shrink_guard_fails_closed_without_previous_manifest(tmp_path: Path) -> None:
    candidate, previous = tmp_path / "candidate.json", tmp_path / "missing.json"
    _manifest(candidate, 10_000)
    result = _run(candidate, previous)
    assert result.returncode != 0
    assert "previous catalog manifest is unavailable" in result.stderr


def test_catalog_shrink_guard_rejects_invalid_previous_manifest(tmp_path: Path) -> None:
    candidate, previous = tmp_path / "candidate.json", tmp_path / "previous.json"
    _manifest(candidate, 10_000)
    previous.write_text("{invalid", encoding="utf-8")
    result = _run(candidate, previous)
    assert result.returncode != 0
    assert "cannot use previous manifest" in result.stderr
