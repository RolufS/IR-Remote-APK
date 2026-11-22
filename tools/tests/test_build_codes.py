import subprocess
from pathlib import Path

from build_codes import git_commit

REPO = Path(__file__).resolve().parents[2]


def _git(*args, cwd):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout.strip()


def test_git_commit_of_checkout_top_level(tmp_path):
    repo = tmp_path / "with space"  # wie "C:/VS Code/..."
    repo.mkdir()
    _git("init", "-q", cwd=repo)
    (repo / "a").write_text("a")
    _git("add", "a", cwd=repo)
    _git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "a", cwd=repo)
    assert git_commit(repo) == _git("rev-parse", "HEAD", cwd=repo)


def test_git_commit_of_nested_plain_dir_is_unknown(tmp_path):
    _git("init", "-q", cwd=tmp_path)
    (tmp_path / "a").write_text("a")
    _git("add", "a", cwd=tmp_path)
    _git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "a", cwd=tmp_path)
    nested = tmp_path / "sub" / "data"
    nested.mkdir(parents=True)
    assert git_commit(nested) == "unknown"


def test_git_commit_of_fixture_dir_inside_repo_is_unknown():
    assert git_commit(REPO / "tools" / "tests" / "fixtures" / "flipper") == "unknown"


def test_git_commit_of_missing_dir_is_unknown(tmp_path):
    assert git_commit(tmp_path / "missing") == "unknown"


def test_gitattributes_line_endings():
    out = _git("check-attr", "eol", "text", "--", "tools/fetch_sources.sh", "gradlew", "x.db", cwd=REPO)
    assert "tools/fetch_sources.sh: eol: lf" in out and "gradlew: eol: lf" in out
    assert "x.db: text: unset" in out
