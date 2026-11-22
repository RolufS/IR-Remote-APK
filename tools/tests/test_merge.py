from pathlib import Path

import pytest

from irdata.merge import coverage, merge
from irdata.protocols import encode_flipper
from irdata.sources import iter_flipper, iter_irdb

FIX = Path(__file__).parent / "fixtures"


def load():
    files = list(iter_flipper(FIX / "flipper")) + list(iter_irdb(FIX / "irdb"))
    return files, merge(files)


def test_samsung_files_merge_into_one_set():
    _, r = load()
    first = r.code_sets[0]
    assert first.support == 3 and first.brand_support == {"Samsung": 3}
    assert {"MENU", "SOURCE", "X:Netflix"} <= first.keys.keys() and "HDMI1" not in first.keys


def test_raw_only_file_is_dropped_not_duplicated():
    _, r = load()
    c = [cs for cs in r.code_sets if cs.keys["POWER"] == encode_flipper("NEC", 0x40, 0x12)]
    assert len(c) == 1 and c[0].support == 1


def test_global_order():
    _, r = load()
    # 0 Samsung-Haupt (3), 1 Vestel/Hisense (2), dann Support 1 nach Markenname: 2 Acme, 3 Philips, 4 Samsung_C, 5 Telefunken
    assert [cs.support for cs in r.code_sets] == [3, 2, 1, 1, 1, 1]
    assert r.code_sets[1].brand_support == {"Vestel": 1, "Hisense": 1}


def test_brand_lists():
    _, r = load()
    assert r.brand_sets["Samsung"] == [(0, False), (4, False)]
    assert r.brand_sets["Telefunken"] == [(5, False), (1, True)]
    assert r.brand_sets["Vestel"] == [(1, False), (5, True)]
    assert r.brand_sets["Toshiba"] == [(1, True), (5, True)]   # nur über Familie
    assert "Thomson" not in r.brand_sets                       # TCL hat keine Code-Sätze


def test_variants_order():
    _, r = load()
    disc = r.variant_order[(0, "Samsung")]
    assert len(disc) == 1 and set(r.variants[disc[0]].keys) == {"HDMI1", "HDMI2", "HDMI3", "HDMI4"}
    samsung_c = next(i for i, _ in r.brand_sets["Samsung"] if i != 0)
    assert r.variant_order[(samsung_c, "Samsung")] == disc     # nur Marken-Variante
    assert set(r.variants[r.variant_order[(1, "Hisense")][0]].keys) == {"HDMI1", "HDMI2"}


def test_scan_dedup_and_labels():
    _, r = load()
    assert [label for _, label, _ in r.scan] == ["Samsung", "Hisense", "Acme", "Philips", "Samsung", "Telefunken"]


def test_dropped_and_coverage():
    files, r = load()
    assert r.dropped_files == 2          # Philips_Weird (nichts sendbar), Samsung_Raw (1 Grundtaste, keine Variante)
    assert coverage(files) == pytest.approx(9 / 10)
