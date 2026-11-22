import sqlite3
import subprocess
import sys
from pathlib import Path

from irdata.db import write_db
from irdata.merge import merge
from irdata.protocols import encode_flipper
from irdata.signal import from_blob
from irdata.sources import iter_flipper, iter_irdb

FIX = Path(__file__).parent / "fixtures"
TOOLS = Path(__file__).resolve().parents[1]


def build(tmp_path):
    files = list(iter_flipper(FIX / "flipper")) + list(iter_irdb(FIX / "irdb"))
    out = tmp_path / "codes.db"
    meta = {"flipper_commit": "f1", "irdb_commit": "i1", "built_at": "2026-01-01T00:00:00Z"}
    write_db(merge(files), out, meta)
    return sqlite3.connect(out)


def test_scan_and_brands(tmp_path):
    db = build(tmp_path)
    assert db.execute("SELECT COUNT(*) FROM scan").fetchone()[0] == 6
    assert db.execute("SELECT hotel_rank FROM brand WHERE name='Samsung'").fetchone() == (1,)
    assert db.execute("SELECT hotel_rank FROM brand WHERE name='Acme'").fetchone() == (None,)
    names = [r[0] for r in db.execute("SELECT name FROM brand ORDER BY id")]
    assert names == sorted(names, key=str.lower)
    assert db.execute("SELECT MIN(position) FROM scan").fetchone() == (0,)


def test_power_signal_blob(tmp_path):
    db = build(tmp_path)
    (blob,) = db.execute(
        "SELECT s.frame FROM code_set_key k JOIN signal s ON s.id=k.signal_id "
        "WHERE k.code_set_id=1 AND k.key='POWER'"
    ).fetchone()
    assert from_blob(blob) == encode_flipper("Samsung32", 7, 2).frame


def test_variant_order_and_meta(tmp_path):
    db = build(tmp_path)
    n = db.execute(
        "SELECT COUNT(*) FROM input_variant_order o JOIN brand b ON b.id=o.brand_id "
        "WHERE o.code_set_id=1 AND b.name='Samsung'"
    ).fetchone()[0]
    assert n == 1
    meta = dict(db.execute("SELECT k, v FROM meta"))
    assert meta["irdb_commit"] == "i1" and meta["flipper_commit"] == "f1"
    assert meta["code_sets"] == "6" and int(meta["brands"]) > 0
    assert db.execute("SELECT COUNT(*) FROM signal").fetchone()[0] == db.execute(
        "SELECT COUNT(*) FROM (SELECT DISTINCT freq, frame, repeat, frame_alt, period_ms FROM signal)"
    ).fetchone()[0]


def test_overwrites_existing_file(tmp_path):
    build(tmp_path).close()
    db = build(tmp_path)
    assert db.execute("SELECT COUNT(*) FROM code_set").fetchone()[0] == 6


def test_cli_report_and_exit_code(tmp_path):
    cmd = [sys.executable, str(TOOLS / "build_codes.py"), "--flipper", str(FIX / "flipper"),
           "--irdb", str(FIX / "irdb"), "--out", str(tmp_path / "o.db")]
    ok = subprocess.run(cmd + ["--min-coverage", "0"], capture_output=True, text=True, encoding="utf-8")
    assert ok.returncode == 0 and "Code-Sätze: 6" in ok.stdout and "Suchlauf: 6" in ok.stdout
    low = subprocess.run(cmd + ["--min-coverage", "0.95"], capture_output=True, text=True, encoding="utf-8")
    assert low.returncode == 2
