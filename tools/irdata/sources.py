"""Liest Flipper-IRDB (.ir) und irdb (.csv) in RemoteFile-Objekte."""
import csv
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator

from .brands import canonical_brand
from .keys import BASIC_KEYS, canonical_key
from .protocols import UnsupportedProtocol, encode_flipper, encode_irdb, from_raw
from .signal import Signal

_TV_DIR = re.compile(r"(?i)\btv\b|television")
_POWER_SLOTS = {"POWER", "POWER_ON", "POWER_OFF"}
MALFORMED = "<malformed>"  # Schluessel in RemoteFile.unsupported fuer kaputte Eintraege


@dataclass
class RemoteFile:
    source: str
    brand: str
    path: str
    keys: dict[str, Signal] = field(default_factory=dict)
    basic_named: int = 0
    entries: int = 0
    unsupported: dict[str, int] = field(default_factory=dict)


class _Builder:
    def __init__(self, source: str, brand: str, path: Path):
        self.file = RemoteFile(source, brand, str(path))
        self._slots: set[str] = set()

    def add(self, raw_name: str | None, make, protocol: str | None) -> None:
        """`make` builds the Signal; `protocol` names it for the unsupported statistics.
        Entries with missing or unparseable fields are counted under MALFORMED and skipped."""
        f = self.file
        f.entries += 1
        if not raw_name:
            self._count(MALFORMED)
            return
        key = canonical_key(raw_name)
        if key is None:
            return
        slot = "POWER" if key in _POWER_SLOTS else key
        if slot in BASIC_KEYS:
            self._slots.add(slot)
        try:
            signal = make()
        except UnsupportedProtocol:
            self._count(protocol or "raw")
            return
        except (KeyError, ValueError, TypeError):
            self._count(MALFORMED)
            return
        f.keys.setdefault(key, signal)

    def _count(self, name: str) -> None:
        self.file.unsupported[name] = self.file.unsupported.get(name, 0) + 1

    def done(self) -> RemoteFile:
        self.file.basic_named = len(self._slots)
        return self.file


def _le(text: str) -> int:
    return int.from_bytes(bytes(int(b, 16) for b in text.split()), "little")


def _raw_signal(e: dict[str, str]) -> Signal:
    freq = int(e["frequency"])
    data = [int(x) for x in e["data"].split()]
    if freq <= 0 or not data or min(data) <= 0:
        raise ValueError("bad raw signal")
    return from_raw(freq, data)


def _flipper_entries(text: str) -> Iterator[dict[str, str]]:
    current: dict[str, str] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            if current:
                yield current
                current = {}
            continue
        k, _, v = line.partition(":")
        k = k.strip().lower()
        if k == "name" and current:
            yield current
            current = {}
        current[k] = v.strip()
    if current:
        yield current


def _read_flipper(path: Path, brand: str) -> RemoteFile:
    b = _Builder("flipper", brand, path)
    for e in _flipper_entries(path.read_text(encoding="utf-8", errors="replace")):
        if "name" not in e:
            continue
        kind = e.get("type")
        if kind == "parsed":
            b.add(
                e["name"],
                lambda e=e: encode_flipper(e["protocol"], _le(e["address"]), _le(e["command"])),
                e.get("protocol"),
            )
        elif kind == "raw":
            b.add(e["name"], lambda e=e: _raw_signal(e), None)
    return b.done()


def iter_flipper(root: Path) -> Iterator[RemoteFile]:
    tvs = Path(root) / "TVs"
    for path in sorted(tvs.rglob("*.ir")):
        parts = path.relative_to(tvs).parts
        if len(parts) < 2:  # Datei direkt unter TVs/: keine Marke bekannt
            continue
        brand = parts[0]
        yield _read_flipper(path, canonical_brand(brand))


def _read_irdb(path: Path, brand: str) -> RemoteFile:
    b = _Builder("irdb", brand, path)
    with path.open(encoding="utf-8", errors="replace", newline="") as fh:
        for row in csv.DictReader(fh):
            b.add(
                row.get("functionname"),
                lambda row=row: encode_irdb(
                    row["protocol"], int(row["device"]), int(row["subdevice"]), int(row["function"])
                ),
                row.get("protocol"),
            )
    return b.done()


def iter_irdb(root: Path) -> Iterator[RemoteFile]:
    codes = Path(root) / "codes"
    for path in sorted(codes.glob("*/*/*.csv")):
        brand, kind = path.relative_to(codes).parts[:2]
        if _TV_DIR.search(kind):
            yield _read_irdb(path, canonical_brand(brand))
