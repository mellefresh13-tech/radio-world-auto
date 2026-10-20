from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def test_external_github_actions_are_pinned_to_full_commit_shas() -> None:
    workflow_dir = REPOSITORY_ROOT / ".github" / "workflows"
    workflow_files = sorted(workflow_dir.glob("*.yml"))
    assert workflow_files, "expected tracked GitHub Actions workflows"

    for workflow_path in workflow_files:
        for line_number, line in enumerate(
            workflow_path.read_text(encoding="utf-8").splitlines(), start=1
        ):
            if not re.match(r"^\s*uses:\s*", line):
                continue
            match = re.search(r"uses:\s*([^\s#]+)", line)
            assert match is not None, f"{workflow_path}:{line_number}: malformed uses"
            action_ref = match.group(1)
            if action_ref.startswith("./") or action_ref.startswith("docker://"):
                continue
            assert "@" in action_ref, (
                f"{workflow_path}:{line_number}: external action is not pinned"
            )
            sha = action_ref.rsplit("@", 1)[1]
            assert re.fullmatch(r"[0-9a-f]{40}", sha), (
                f"{workflow_path}:{line_number}: action must use a full immutable SHA"
            )


def test_temporary_signed_apk_cannot_masquerade_as_release_artifact() -> None:
    workflow = (
        REPOSITORY_ROOT / ".github" / "workflows" / "android.yml"
    ).read_text(encoding="utf-8")

    assert "permissions:\n  contents: read" in workflow
    assert "signing_mode=persistent" in workflow
    assert "signing_mode=temporary" in workflow
    assert "radio-world-auto-test-build" in workflow
    assert "radio-world-auto-release" in workflow
    assert "temporary-release-password" not in workflow
    assert "openssl rand -hex 32" in workflow


def test_crash_report_is_excluded_from_cloud_legacy_and_device_backups() -> None:
    manifest = ET.parse(
        REPOSITORY_ROOT / "android" / "app" / "src" / "main"
        / "AndroidManifest.xml"
    ).getroot()
    application = manifest.find("application")
    assert application is not None
    assert application.get(ANDROID_NS + "fullBackupContent") == "@xml/backup_rules"
    assert application.get(ANDROID_NS + "dataExtractionRules") == "@xml/data_extraction_rules"

    legacy_rules = ET.parse(
        REPOSITORY_ROOT / "android" / "app" / "src" / "main" / "res"
        / "xml" / "backup_rules.xml"
    ).getroot()
    assert legacy_rules.tag == "full-backup-content"
    legacy_excludes = {
        (item.get("domain"), item.get("path"))
        for item in legacy_rules.findall("exclude")
    }
    assert ("file", "last_crash.txt") in legacy_excludes

    extraction_rules = ET.parse(
        REPOSITORY_ROOT / "android" / "app" / "src" / "main" / "res"
        / "xml" / "data_extraction_rules.xml"
    ).getroot()
    for section_name in ("cloud-backup", "device-transfer"):
        section = extraction_rules.find(section_name)
        assert section is not None, f"missing {section_name} rules"
        excludes = {
            (item.get("domain"), item.get("path"))
            for item in section.findall("exclude")
        }
        assert ("file", "last_crash.txt") in excludes

def test_catalog_build_is_read_only_and_only_minimal_publisher_can_write() -> None:
    workflow = (
        REPOSITORY_ROOT / ".github" / "workflows" / "catalog.yml"
    ).read_text(encoding="utf-8")

    assert "permissions:\n  contents: read" in workflow
    assert "jobs:\n  build-catalog:" in workflow
    assert "  publish-catalog:" in workflow

    build_section = workflow.split("jobs:\n  build-catalog:", 1)[1].split(
        "\n  publish-catalog:", 1
    )[0]
    publisher_section = workflow.split("\n  publish-catalog:", 1)[1]

    assert "pip install -r collector/requirements.txt" in build_section
    assert "python -m radio_catalog.cli" in build_section
    assert "git push origin HEAD:catalog-data --force" not in build_section
    assert "contents: write" not in build_section

    assert "permissions:\n      contents: write" in publisher_section
    assert "actions/download-artifact@" in publisher_section
    assert "git push origin HEAD:catalog-data --force" in publisher_section
    assert "pip install" not in publisher_section
    assert "python -m radio_catalog.cli" not in publisher_section

