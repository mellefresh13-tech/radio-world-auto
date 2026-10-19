from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


def _workflow_text(name: str) -> str:
    return (REPOSITORY_ROOT / ".github" / "workflows" / name).read_text(encoding="utf-8")


def test_catalog_publication_is_main_guarded_and_concurrency_is_ref_scoped() -> None:
    workflow = _workflow_text("catalog.yml")

    assert "group: catalog-${{ github.ref }}" in workflow
    assert "if: github.ref == 'refs/heads/main'" in workflow
    assert "workflow_dispatch:" in workflow


def test_collector_test_runs_are_isolated_by_git_ref() -> None:
    workflow = _workflow_text("collector.yml")

    assert "group: collector-${{ github.ref }}" in workflow
    assert "cancel-in-progress: true" in workflow
