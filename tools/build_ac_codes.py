"""Baut die Klima-Datenbank: python tools/build_ac_codes.py --smartir DIR --out PATH

DIR ist der SmartIR-Klon (oder direkt ein Ordner mit Klima-JSON-Dateien)."""
import argparse
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from build_codes import git_commit  # noqa: E402
from irdata import acblob  # noqa: E402
from irdata.acdb import write_ac_db  # noqa: E402
from irdata.smartir import Stats, merge_devices, read_all  # noqa: E402

SOURCE = "SmartIR (https://github.com/smartHomeHub/SmartIR), codes/climate"
LICENSE = "MIT, Copyright (c) 2019 Vassilis Panos"
F_BLOB = "zu viele verschiedene Dauern"
MAX_ASSET_BYTES = 5 * 1024 * 1024


def _report(title: str, counts) -> None:
    items = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
    print(f"{title}: {sum(v for k, v in items if ': ' not in k)}")
    for k, v in items:
        print(f"  {k}: {v}")


def main(argv: list[str] | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser()
    p.add_argument("--smartir", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--built-at", help="fester Zeitstempel (reproduzierbare Fixtures)")
    a = p.parse_args(argv)

    climate = a.smartir / "codes" / "climate"
    folder = climate if climate.is_dir() else a.smartir
    stats = Stats()
    read = read_all(folder, stats)
    devices = []
    for d in merge_devices(read):
        try:
            acblob.encode(d.commands)
        except acblob.BlobError:
            stats.skipped_files[F_BLOB] += len(d.sources)
            continue
        devices.append(d)

    meta = {
        "source": SOURCE,
        "license": LICENSE,
        "smartir_commit": git_commit(a.smartir),
        "built_at": a.built_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    write_ac_db(devices, a.out, meta)

    size = a.out.stat().st_size
    print(f"Dateien: {stats.files}")
    _report("Übersprungene Dateien", stats.skipped_files)
    _report("Übersprungene Kommandos", stats.skipped_commands)
    print(f"Geräte gelesen: {len(read)}")
    print(f"Geräte nach Zusammenführen: {len(devices)}")
    print(f"Marken: {len({d.brand for d in devices})}")
    print(f"Kommandos: {sum(len(d.commands) for d in devices)}")
    print(f"Asset: {size} Bytes ({size / 1024 / 1024:.2f} MB)")
    if size > MAX_ASSET_BYTES:
        print("Asset größer als 5 MB", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
