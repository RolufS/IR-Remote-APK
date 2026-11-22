"""Baut die Code-Datenbank: python tools/build_codes.py --flipper DIR [--irdb DIR] --out PATH"""
import argparse
import subprocess
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from irdata.db import write_db  # noqa: E402
from irdata.merge import coverage, merge  # noqa: E402
from irdata.sources import iter_flipper, iter_irdb  # noqa: E402


def git_commit(directory: Path) -> str:
    """HEAD of `directory`, but only if it is itself the top level of a git checkout."""
    try:
        r = subprocess.run(
            ["git", "-C", str(directory), "rev-parse", "--show-toplevel", "HEAD"],
            capture_output=True, text=True, check=True,
        )
        top, head = r.stdout.splitlines()[-2:]
        if Path(top).resolve() == Path(directory).resolve():
            return head
    except (OSError, ValueError, subprocess.CalledProcessError):
        pass
    return "unknown"


def main(argv: list[str] | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser()
    p.add_argument("--flipper", type=Path, required=True)
    p.add_argument("--irdb", type=Path)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--min-coverage", type=float, default=0.95)
    a = p.parse_args(argv)

    flipper = list(iter_flipper(a.flipper))
    irdb = list(iter_irdb(a.irdb)) if a.irdb else []
    files = flipper + irdb
    result = merge(files)
    cov = coverage(files)

    meta = {
        "flipper_commit": git_commit(a.flipper),
        "irdb_commit": git_commit(a.irdb) if a.irdb else "",
        "built_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "coverage": f"{cov:.4f}",
    }
    write_db(result, a.out, meta)

    unsupported: Counter[str] = Counter()
    for f in files:
        unsupported.update(f.unsupported)
    unsup = ", ".join(f"{k} {v}" for k, v in sorted(unsupported.items(), key=lambda kv: (-kv[1], kv[0])))
    print(f"Dateien: Flipper {len(flipper)}, irdb {len(irdb)}")
    print(f"Verworfene Dateien: {result.dropped_files}")
    print(f"Nicht unterstützte Protokolle: {unsup or 'keine'}")
    print(f"Code-Sätze: {len(result.code_sets)}")
    print(f"Marken: {len(result.brand_sets)}")
    print(f"Varianten: {len(result.variants)}")
    print(f"Suchlauf: {len(result.scan)}")
    print(f"Abdeckung: {cov * 100:.1f} %")

    if cov < a.min_coverage:
        print(f"Abdeckung unter Minimum ({a.min_coverage * 100:.1f} %)", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
