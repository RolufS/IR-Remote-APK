"""Schreibt die Klima-Datenbank ac_codes.db (Schema ist der Vertrag zu de.hotelremote.data.AcCodeDatabase)."""
import json
import os
import sqlite3
import tempfile
from pathlib import Path

from . import acblob
from .smartir import AcDevice

SCHEMA = """
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE ac_brand(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, sort_name TEXT NOT NULL);
CREATE TABLE ac_device(
  id INTEGER PRIMARY KEY,
  brand_id INTEGER NOT NULL REFERENCES ac_brand(id),
  models TEXT NOT NULL,       -- JSON-Liste
  source TEXT NOT NULL,       -- SmartIR-Dateiname(n), kommagetrennt
  min_temp REAL NOT NULL,
  max_temp REAL NOT NULL,
  precision REAL NOT NULL,
  modes TEXT NOT NULL,        -- JSON-Liste
  fan_modes TEXT NOT NULL,    -- JSON-Liste
  swing_modes TEXT NOT NULL,  -- JSON-Liste, leer = Geraet ohne Swing-Ebene
  commands BLOB NOT NULL      -- siehe irdata/acblob.py
);
CREATE INDEX ac_device_brand ON ac_device(brand_id);
"""


def sort_name(name: str) -> str:
    return name.casefold()


def ordered(devices: list[AcDevice]) -> list[AcDevice]:
    """Nach Marke, dann Geraete mit mehr Modellen zuerst, dann Quelldatei."""
    return sorted(devices, key=lambda d: (sort_name(d.brand), d.brand, -len(d.models), d.sources[0]))


def write_ac_db(devices: list[AcDevice], out: Path, meta: dict[str, str]) -> None:
    """Schreibt in eine Temp-Datei neben `out` und ersetzt `out` anschliessend."""
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(suffix=".tmp", dir=out.parent)
    os.close(fd)
    try:
        con = sqlite3.connect(tmp)
        try:
            con.executescript(SCHEMA)
            devices = ordered(devices)
            brands = sorted({d.brand for d in devices}, key=lambda n: (sort_name(n), n))
            brand_id = {n: i + 1 for i, n in enumerate(brands)}
            con.executemany("INSERT INTO ac_brand VALUES (?,?,?)",
                            [(brand_id[n], n, sort_name(n)) for n in brands])
            for i, d in enumerate(devices):
                con.execute(
                    "INSERT INTO ac_device VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    (i + 1, brand_id[d.brand], json.dumps(d.models, ensure_ascii=False), ",".join(d.sources),
                     d.min_temp, d.max_temp, d.precision, json.dumps(d.modes, ensure_ascii=False),
                     json.dumps(d.fan_modes, ensure_ascii=False), json.dumps(d.swing_modes, ensure_ascii=False),
                     acblob.encode(d.commands)),
                )
            rows = dict(meta)
            rows["devices"] = str(len(devices))
            rows["brands"] = str(len(brands))
            rows["blob_version"] = str(acblob.VERSION)
            con.executemany("INSERT INTO meta VALUES (?,?)", sorted(rows.items()))
            con.commit()
            con.execute("VACUUM")
        finally:
            con.close()
        os.replace(tmp, out)
    except BaseException:
        Path(tmp).unlink(missing_ok=True)
        raise
