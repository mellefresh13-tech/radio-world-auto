from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIREMENTS_FILES = (
    ROOT / "requirements.txt",
    ROOT / "api" / "requirements.txt",
    ROOT / "api" / "requirements-dev.txt",
    ROOT / "collector" / "requirements.txt",
)
PIN_PATTERN = re.compile(
    r"^([A-Za-z0-9][A-Za-z0-9._-]*(?:\[[A-Za-z0-9_,.-]+\])?)=="
    r"([A-Za-z0-9][A-Za-z0-9.!+_-]*)(?:\s*;\s*(.+))?$"
)


def _pins(path: Path) -> dict[str, str]:
    pins: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue

        match = PIN_PATTERN.fullmatch(line)
        assert match is not None, (
            f"{path.relative_to(ROOT)}:{line_number} must pin an exact version with '==': {line}"
        )
        name, version, _marker = match.groups()
        normalized_name = name.split("[", maxsplit=1)[0].lower().replace("_", "-").replace(".", "-")
        assert normalized_name not in pins, (
            f"{path.relative_to(ROOT)} contains duplicate pins for {normalized_name}"
        )
        pins[normalized_name] = version
    return pins


def test_python_requirements_use_exact_version_pins() -> None:
    for path in REQUIREMENTS_FILES:
        assert path.is_file(), f"Missing dependency manifest: {path.relative_to(ROOT)}"
        pins = _pins(path)
        assert pins, f"No pinned dependencies found in {path.relative_to(ROOT)}"


def test_shared_dependency_versions_match_across_manifests() -> None:
    manifests = {path.relative_to(ROOT).as_posix(): _pins(path) for path in REQUIREMENTS_FILES}
    all_names = set().union(*(pins.keys() for pins in manifests.values()))

    for name in sorted(all_names):
        versions = {
            manifest_name: pins[name]
            for manifest_name, pins in manifests.items()
            if name in pins
        }
        assert len(set(versions.values())) == 1, (
            f"Shared dependency {name} has inconsistent pins: {versions}"
        )


def test_root_runtime_requirements_match_api_runtime_subset() -> None:
    root_pins = _pins(ROOT / "requirements.txt")
    api_pins = _pins(ROOT / "api" / "requirements.txt")
    test_only = {"iniconfig", "packaging", "pluggy", "pygments", "pytest"}

    assert root_pins == {name: version for name, version in api_pins.items() if name not in test_only}
