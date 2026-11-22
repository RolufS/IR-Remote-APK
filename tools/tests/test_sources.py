from pathlib import Path

from irdata.brands import canonical_brand, family_members
from irdata.protocols import encode_flipper, encode_irdb
from irdata.sources import iter_flipper, iter_irdb

FIX = Path(__file__).parent / "fixtures"


def _flipper(name):
    return next(r for r in iter_flipper(FIX / "flipper") if r.path.endswith(name))


def test_flipper_parsed_file():
    f = _flipper("Samsung_A.ir")
    assert f.brand == "Samsung" and f.source == "flipper"
    assert f.keys["POWER"] == encode_flipper("Samsung32", 7, 2) and "X:Netflix" in f.keys
    assert f.basic_named == 6 and f.entries == 8 and f.unsupported == {}


def test_flipper_unsupported_counted():
    f = _flipper("Philips_Weird.ir")
    assert f.keys == {} and f.unsupported == {"Pioneer": 4} and f.basic_named == 4


def test_flipper_raw_is_canonicalized():
    f = _flipper("Samsung_Raw.ir")
    assert f.keys["POWER"] == encode_flipper("NEC", 0x40, 0x12)


def test_flipper_order_is_deterministic():
    paths = [r.path for r in iter_flipper(FIX / "flipper")]
    assert paths == sorted(paths) and len(paths) == 10


def test_irdb_csv():
    files = {r.brand: r for r in iter_irdb(FIX / "irdb")}
    assert files["Acme"].keys["MUTE"] == encode_irdb("NEC1", 9, -1, 2)
    assert files["Samsung"].keys["POWER"] == encode_flipper("Samsung32", 7, 2)
    assert files["Samsung"].source == "irdb" and files["Samsung"].basic_named == 4


def test_canonical_brand_and_families():
    assert canonical_brand("SAMSUNG") == "Samsung" and canonical_brand("lg") == "LG"
    assert "Vestel" in family_members("Telefunken") and "Telefunken" in family_members("Vestel")
    assert "Telefunken" not in family_members("Telefunken")
