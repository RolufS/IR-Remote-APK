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


_GOOD = "name: Power\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\ncommand: 08 00 00 00\n#\n"
_MALFORMED = [
    "name: Mute\ntype: parsed\naddress: 04 00 00 00\ncommand: 0D 00 00 00\n",  # kein Protokoll
    "name: Vol_up\ntype: parsed\nprotocol: NEC\ncommand: 02 00 00 00\n",  # keine Adresse
    "name: Vol_dn\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\n",  # kein Kommando
    "name: Ch_next\ntype: parsed\nprotocol: NEC\naddress: ZZ 00\ncommand: 00\n",  # kaputtes Hex
    "name: Ch_prev\ntype: raw\nfrequency: 38000\nduty_cycle: 0.33\n",  # keine Daten
    "name: Info\ntype: raw\nfrequency: 38000\ndata: 900 abc 500\n",  # kein Integer
    "name: Menu\ntype: raw\nfrequency: 38k\ndata: 900 450 500\n",  # Frequenz kaputt
    "name: Exit\ntype: raw\nfrequency: 38000\ndata: \n",  # leere Daten
    "name: Back\ntype: raw\nfrequency: 38000\ndata: 900 -450 500\n",  # negative Dauer
]


def test_flipper_malformed_entries_are_counted_not_fatal(tmp_path):
    d = tmp_path / "TVs" / "Acme"
    d.mkdir(parents=True)
    text = "Filetype: IR signals file\nVersion: 1\n#\n" + _GOOD + "#\n".join(_MALFORMED)
    (d / "Acme_1.ir").write_text(text, encoding="utf-8")
    (d / "Acme_2.ir").write_text("Filetype: IR signals file\n#\n" + _GOOD, encoding="utf-8")
    files = list(iter_flipper(tmp_path))
    assert [Path(f.path).name for f in files] == ["Acme_1.ir", "Acme_2.ir"]
    f = files[0]
    assert f.keys == {"POWER": encode_flipper("NEC", 4, 8)}
    assert f.unsupported == {"<malformed>": len(_MALFORMED)} and f.entries == 1 + len(_MALFORMED)


def test_flipper_file_without_brand_folder_is_skipped(tmp_path):
    (tmp_path / "TVs" / "Acme").mkdir(parents=True)
    (tmp_path / "TVs" / "Loose.ir").write_text(_GOOD, encoding="utf-8")
    (tmp_path / "TVs" / "Acme" / "Acme_1.ir").write_text(_GOOD, encoding="utf-8")
    assert [Path(f.path).name for f in iter_flipper(tmp_path)] == ["Acme_1.ir"]


def test_irdb_malformed_rows_are_counted_not_fatal(tmp_path):
    d = tmp_path / "codes" / "Acme" / "TV"
    d.mkdir(parents=True)
    (d / "1,-1.csv").write_text(
        "functionname,protocol,device,subdevice,function\n"
        "POWER,NEC1,9,-1,1\n"
        "MUTE,NEC1,9,,2\n"  # leeres Feld
        "VOLUME +,NEC1,x,-1,3\n"  # kein Integer
        "VOLUME -,NEC1,9\n",  # zu kurze Zeile
        encoding="utf-8",
    )
    (f,) = iter_irdb(tmp_path)
    assert f.keys == {"POWER": encode_irdb("NEC1", 9, -1, 1)}
    assert f.unsupported == {"<malformed>": 3}


def test_flipper_raw_longer_than_two_seconds_is_malformed(tmp_path):
    # Android (ConsumerIrService) lehnt Muster ueber 2 s Gesamtdauer ab.
    d = tmp_path / "TVs" / "Acme"
    d.mkdir(parents=True)
    too_long = "name: Mute\ntype: raw\nfrequency: 38000\ndata: 9000 1000000 9000 1000000 500\n"
    at_limit = "name: Vol_up\ntype: raw\nfrequency: 38000\ndata: 3000 1000000 3000 994000\n"
    (d / "Acme_1.ir").write_text("Filetype: IR signals file\n#\n" + too_long + "#\n" + at_limit, encoding="utf-8")
    f = next(iter_flipper(tmp_path))
    assert "MUTE" not in f.keys and f.unsupported == {"<malformed>": 1}
    assert sum(f.keys["VOL_UP"].frame) <= 2_000_000
