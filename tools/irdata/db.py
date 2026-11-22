"""Schreibt das MergeResult als SQLite-Datei (Schema ist der Vertrag zur App)."""
import os
import sqlite3
import tempfile
from pathlib import Path

from .brands import HOTEL_BRANDS
from .merge import MergeResult
from .signal import Signal, to_blob

SCHEMA = """
CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT NOT NULL);
CREATE TABLE brand(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, hotel_rank INTEGER);
CREATE TABLE signal(id INTEGER PRIMARY KEY, freq INTEGER NOT NULL, frame BLOB NOT NULL, repeat BLOB, frame_alt BLOB, period_ms INTEGER NOT NULL);
CREATE TABLE code_set(id INTEGER PRIMARY KEY, support INTEGER NOT NULL);
CREATE TABLE code_set_brand(code_set_id INTEGER NOT NULL, brand_id INTEGER NOT NULL, ord INTEGER NOT NULL, via_family INTEGER NOT NULL, PRIMARY KEY(code_set_id, brand_id));
CREATE TABLE code_set_key(code_set_id INTEGER NOT NULL, key TEXT NOT NULL, signal_id INTEGER NOT NULL, PRIMARY KEY(code_set_id, key));
CREATE TABLE input_variant(id INTEGER PRIMARY KEY, support INTEGER NOT NULL);
CREATE TABLE input_variant_key(variant_id INTEGER NOT NULL, key TEXT NOT NULL, signal_id INTEGER NOT NULL, PRIMARY KEY(variant_id, key));
CREATE TABLE input_variant_order(code_set_id INTEGER NOT NULL, brand_id INTEGER NOT NULL, ord INTEGER NOT NULL, variant_id INTEGER NOT NULL, PRIMARY KEY(code_set_id, brand_id, ord));
CREATE TABLE scan(position INTEGER PRIMARY KEY, code_set_id INTEGER NOT NULL, brand_id INTEGER NOT NULL, signal_id INTEGER NOT NULL);
"""


def _populate(con: sqlite3.Connection, result: MergeResult, meta: dict[str, str]) -> None:
    signal_ids: dict[tuple, int] = {}

    def signal_id(s: Signal) -> int:
        row = (
            s.freq,
            to_blob(s.frame),
            None if s.repeat is None else to_blob(s.repeat),
            None if s.frame_alt is None else to_blob(s.frame_alt),
            s.period_ms,
        )
        if row not in signal_ids:
            signal_ids[row] = len(signal_ids) + 1
            con.execute("INSERT INTO signal VALUES (?,?,?,?,?,?)", (signal_ids[row], *row))
        return signal_ids[row]

    brand_names = sorted(result.brand_sets, key=lambda n: (n.lower(), n))
    brand_id = {n: i + 1 for i, n in enumerate(brand_names)}
    for n in brand_names:
        rank = HOTEL_BRANDS.index(n) + 1 if n in HOTEL_BRANDS else None
        con.execute("INSERT INTO brand VALUES (?,?,?)", (brand_id[n], n, rank))

    for i, cs in enumerate(result.code_sets):
        con.execute("INSERT INTO code_set VALUES (?,?)", (i + 1, cs.support))
        for key, sig in cs.keys.items():
            con.execute("INSERT INTO code_set_key VALUES (?,?,?)", (i + 1, key, signal_id(sig)))

    for name, entries in result.brand_sets.items():
        for ord_, (ci, via_family) in enumerate(entries):
            con.execute(
                "INSERT INTO code_set_brand VALUES (?,?,?,?)",
                (ci + 1, brand_id[name], ord_, int(via_family)),
            )

    for i, v in enumerate(result.variants):
        con.execute("INSERT INTO input_variant VALUES (?,?)", (i + 1, v.support))
        for key, sig in v.keys.items():
            con.execute("INSERT INTO input_variant_key VALUES (?,?,?)", (i + 1, key, signal_id(sig)))

    for (ci, name), order in result.variant_order.items():
        for ord_, vi in enumerate(order):
            con.execute(
                "INSERT INTO input_variant_order VALUES (?,?,?,?)",
                (ci + 1, brand_id[name], ord_, vi + 1),
            )

    for pos, (ci, name, sig) in enumerate(result.scan):
        con.execute("INSERT INTO scan VALUES (?,?,?,?)", (pos, ci + 1, brand_id[name], signal_id(sig)))

    rows = dict(meta)
    rows["code_sets"] = str(len(result.code_sets))
    rows["brands"] = str(len(brand_names))
    con.executemany("INSERT INTO meta VALUES (?,?)", rows.items())


def write_db(result: MergeResult, out: Path, meta: dict[str, str]) -> None:
    """Schreibt in eine Temp-Datei neben `out` und ersetzt `out` anschliessend."""
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(suffix=".tmp", dir=out.parent)
    os.close(fd)
    try:
        con = sqlite3.connect(tmp)
        try:
            con.executescript(SCHEMA)
            _populate(con, result, meta)
            con.commit()
            con.execute("VACUUM")
        finally:
            con.close()
        os.replace(tmp, out)
    except BaseException:
        Path(tmp).unlink(missing_ok=True)
        raise
