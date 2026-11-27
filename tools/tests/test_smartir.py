import base64
import json
import random
import sqlite3
import subprocess
import sys
import zlib
from pathlib import Path

import pytest

from irdata import acblob
from irdata.acdb import write_ac_db
from irdata.smartir import (
    C_BASE64, C_EXTRA, C_NOT_IR, C_PATH, C_TOO_LONG, C_TRUNCATED, F_FORMAT, F_JSON, F_NO_OFF,
    CommandError, Stats, broadlink_pulses, decode_base64, merge_devices, normalize_brand, raw_pulses,
    read_all,
)

FIX = Path(__file__).parent / "fixtures" / "smartir"
TOOLS = Path(__file__).resolve().parents[1]
REPO = TOOLS.parent
APP_FIXTURE = REPO / "app" / "src" / "test" / "resources" / "ac-fixture.db"
BUILT_AT = "2026-01-01T00:00:00Z"

# Broadlink-Einheiten -> µs (v * 269000 / 8192, gerundet); dieselben Werte prueft AcCodesTest.kt
US = {274: 8997, 137: 4499, 17: 558, 51: 1675, 34: 1116}
ACME_OFF = (8997, 4499, 558, 1675, 558)
ACME_COOL_AUTO_16 = (8997, 4499, 558, 1675, 558, 558, 558, 558, 558, 558, 558)
BETA_OFF = (4000, 1500, 600)


def packet(units, repeat=0, terminator=True):
    body = bytearray()
    for u in units:
        body += bytes([u]) if 0 < u < 256 else bytes([0, u >> 8, u & 0xFF])
    if terminator:
        body += b"\x00\x0d\x05"
    return bytes([0x26, repeat, len(body) & 0xFF, len(body) >> 8]) + bytes(body)


# ---------------------------------------------------------------- Dekodierung

def test_known_bytes_decode_to_microseconds():
    raw = bytes.fromhex("26000a00000112891133110" "00d05")
    assert broadlink_pulses(raw) == ACME_OFF


def test_two_byte_big_endian_duration():
    assert broadlink_pulses(packet([274, 17, 51])) == (US[274], US[17], US[51])
    assert broadlink_pulses(packet([0x1234, 17, 17]))[0] == round(0x1234 * 269000 / 8192)


def test_terminator_in_mark_position_is_dropped():
    assert broadlink_pulses(packet([274, 17])) == (US[274],)


def test_last_gap_removed_and_long_gaps_capped():
    # Terminator 0x0d05 = 3333 Einheiten ≈ 109 ms ist die letzte Pause -> entfaellt
    assert broadlink_pulses(packet([17, 51, 17])) == (558, 1675, 558)
    # Pause > 100 ms mitten im Muster wird auf 100 ms gekappt
    assert broadlink_pulses(packet([17, 3333, 17])) == (558, 100_000, 558)


def test_repeat_byte_repeats_frame_with_capped_gap():
    assert broadlink_pulses(packet([17, 51, 17], repeat=2)) == (
        558, 1675, 558, 100_000, 558, 1675, 558, 100_000, 558, 1675, 558)


def test_repeat_of_frame_ending_with_mark_inserts_gap():
    assert broadlink_pulses(packet([17, 51, 17], repeat=1, terminator=False)) == (
        558, 1675, 558, 100_000, 558, 1675, 558)


def test_trailing_padding_ignored():
    raw = packet([17, 51, 17]) + b""
    padded = bytes([0x26, 0, len(raw) - 4 + 3, 0]) + raw[4:] + b"\x00\x00\x00"
    assert broadlink_pulses(padded) == (558, 1675, 558)


def test_pattern_longer_than_1_5_s_rejected():
    with pytest.raises(CommandError) as e:
        broadlink_pulses(packet([17, 51] * 700 + [17]))
    assert e.value.reason == C_TOO_LONG


def test_not_ir_and_truncated_rejected():
    with pytest.raises(CommandError) as e:
        broadlink_pulses(b"\xb2\x00\x02\x00\x11\x11")
    assert e.value.reason == C_NOT_IR
    with pytest.raises(CommandError) as e:
        broadlink_pulses(bytes([0x26, 0, 50, 0, 17, 51, 17]))
    assert e.value.reason == C_TRUNCATED


def test_length_field_too_large_but_terminated_is_accepted():
    raw = packet([17, 51, 17])
    raw = raw[:2] + bytes([raw[2] + 16]) + raw[3:]
    assert broadlink_pulses(raw) == (558, 1675, 558)


@pytest.mark.parametrize("mangle", [
    lambda s: s,
    lambda s: s.rstrip("="),  # Padding fehlt
    lambda s: s.rstrip("=") + "====",  # zu viel Padding
    lambda s: s[:10] + "\n " + s[10:] + "\n",  # Leerraum
    lambda s: s + "v",  # Muell nach dem Padding
])
def test_base64_padding_tolerance(mangle):
    raw = packet([274, 137, 17, 51, 17])
    text = base64.b64encode(raw).decode()
    assert text.endswith("=")
    assert decode_base64(mangle(text))[:len(raw)] == raw


def test_base64_foreign_characters_rejected():
    with pytest.raises(CommandError) as e:
        decode_base64("JgAK.AAABEokRMxEADQU=")
    assert e.value.reason == C_BASE64


def test_raw_merges_same_sign_and_trims_gaps():
    assert raw_pulses("[-5000, 3000, 1000, -1500, 600, -200000]") == BETA_OFF
    assert raw_pulses("9000 -4500 560 -150000 560 -45000") == (9000, 4500, 560, 100_000, 560)
    with pytest.raises(CommandError):
        raw_pulses("[9000, -4500, 560")


# ---------------------------------------------------------------- Marken, Zusammenfuehren

@pytest.mark.parametrize("raw,expected", [
    ("Ggeneral Electric", "General Electric"),
    (" Toshiba", "Toshiba"),
    ("FUJITSU", "Fujitsu"),
    ("LG", "LG"),
    ("AUX", "AUX"),
    ("Mitsubishi Electric ", "Mitsubishi Electric"),
    ("Mitsubishi Heavy Industries", "Mitsubishi Heavy"),
    ("Mitsubishi Heavy", "Mitsubishi Heavy"),
    ("Senville (F-native, RG66A2/BGEFU1)", "Senville"),
])
def test_normalize_brand(raw, expected):
    assert normalize_brand(raw) == expected


def read_fixtures():
    stats = Stats()
    return read_all(FIX, stats), stats


def test_fixture_reading_counts_skips():
    devices, stats = read_fixtures()
    assert stats.files == 8
    assert stats.skipped_files[F_JSON] == 1
    assert stats.skipped_files[F_NO_OFF] == 1
    assert stats.skipped_files[F_FORMAT] == 1
    assert stats.skipped_commands[C_TOO_LONG] == 1
    assert stats.skipped_commands[C_PATH] == 1
    assert stats.skipped_commands[C_EXTRA] == 1
    assert len(devices) == 5


def test_tolerant_commands_survive_and_cold_becomes_cool():
    devices, _ = read_fixtures()
    acme = next(d for d in devices if d.sources == ["1000.json"])
    assert acme.commands["off"] == ACME_OFF
    assert acme.commands["cool/auto/16"] == ACME_COOL_AUTO_16
    assert "cool/low/16" in acme.commands and "cool/low/17" in acme.commands
    assert len(acme.commands["heat/low/17"]) == 23 and acme.commands["heat/low/17"][11] == 100_000
    a3 = next(d for d in devices if d.sources == ["1005.json"])
    assert list(a3.commands) == ["off", "cool/auto/20"] and a3.modes == ["cool"]
    ge = next(d for d in devices if d.brand == "General Electric")
    assert set(ge.commands) == {"off", "cool/auto/off/22", "cool/auto/off/22.5", "cool/auto/swing/22",
                                "cool/auto/swing/22.5"}
    assert ge.swing_modes == ["off", "swing"] and ge.precision == 0.5


def test_identical_devices_merge_models():
    devices, _ = read_fixtures()
    merged = merge_devices(devices)
    assert len(merged) == 4
    acme = next(d for d in merged if "1000.json" in d.sources)
    assert acme.brand == "Acme"
    assert acme.models == ["A1", "A2"] and acme.sources == ["1000.json", "1001.json"]


# ---------------------------------------------------------------- Symbole, Blob

def test_clusters_stay_within_tolerance():
    rnd = random.Random(7)
    durations = [int(base * rnd.uniform(0.85, 1.15)) for base in (560, 1690, 4500, 9000) for _ in range(200)]
    mapping = acblob.clusters(durations)
    for d in durations:
        assert abs(mapping[d] - d) <= d * acblob.TOLERANCE
    assert len(set(mapping.values())) <= 12


def test_distinct_durations_keep_exact_values():
    mapping = acblob.clusters([558, 1675, 4499, 8997, 100_000])
    assert all(k == v for k, v in mapping.items())


def test_blob_roundtrip_within_tolerance():
    rnd = random.Random(3)
    commands = {"off": (9000, 4500, 560)}
    for t in range(16, 31):
        commands[f"cool/auto/{t}"] = tuple(
            int(rnd.choice((560, 1690)) * rnd.uniform(0.95, 1.05)) for _ in range(199))
    decoded = acblob.decode(acblob.encode(commands))
    assert list(decoded) == list(commands)
    for path, pulses in commands.items():
        assert len(decoded[path]) == len(pulses)
        assert all(abs(a - b) <= b * acblob.TOLERANCE for a, b in zip(decoded[path], pulses))


def test_blob_layout():
    raw = zlib.decompress(acblob.encode({"off": (500, 1000, 500)}))
    assert raw == bytes([1, 2, 0]) + (500).to_bytes(4, "little") + (1000).to_bytes(4, "little") \
        + bytes([1, 0, 3]) + b"off" + bytes([3, 0, 0, 1, 0])


# ---------------------------------------------------------------- Datenbank, gemeinsame Fixture

def build(out):
    cmd = [sys.executable, str(TOOLS / "build_ac_codes.py"), "--smartir", str(FIX), "--out", str(out),
           "--built-at", BUILT_AT]
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")


def dump(path):
    con = sqlite3.connect(path)
    try:
        return {t: con.execute(f"SELECT * FROM {t} ORDER BY 1").fetchall() for t in ("meta", "ac_brand", "ac_device")}
    finally:
        con.close()


def test_cli_builds_db_with_stats(tmp_path):
    r = build(tmp_path / "ac.db")
    assert r.returncode == 0, r.stderr
    assert "Geräte nach Zusammenführen: 4" in r.stdout and "Marken: 3" in r.stdout and "Asset:" in r.stdout
    db = dump(tmp_path / "ac.db")
    meta = dict(db["meta"])
    assert meta["smartir_commit"] == "unknown" and meta["license"].startswith("MIT")
    assert meta["devices"] == "4" and meta["brands"] == "3"
    assert [b[1] for b in db["ac_brand"]] == ["Acme", "Beta", "General Electric"]
    acme = [d for d in db["ac_device"] if d[1] == 1]
    assert [json.loads(d[2]) for d in acme] == [["A1", "A2"], ["A3"]]
    first = acme[0]
    assert first[3] == "1000.json,1001.json" and first[4:7] == (16.0, 17.0, 1.0)
    assert json.loads(first[7]) == ["cool", "heat"] and json.loads(first[9]) == []
    commands = acblob.decode(first[10])
    assert commands["off"] == ACME_OFF and commands["cool/auto/16"] == ACME_COOL_AUTO_16
    beta = next(d for d in db["ac_device"] if d[1] == 2)
    assert acblob.decode(beta[10])["off"] == BETA_OFF


def test_checked_in_app_fixture_is_current(tmp_path):
    """app/src/test/resources/ac-fixture.db neu erzeugen mit:
    python tools/build_ac_codes.py --smartir tools/tests/fixtures/smartir
        --out app/src/test/resources/ac-fixture.db --built-at 2026-01-01T00:00:00Z"""
    assert build(tmp_path / "ac.db").returncode == 0
    assert dump(tmp_path / "ac.db") == dump(APP_FIXTURE)


def test_write_replaces_existing_file(tmp_path):
    out = tmp_path / "ac.db"
    devices, _ = read_fixtures()
    write_ac_db(merge_devices(devices), out, {"built_at": BUILT_AT})
    write_ac_db(merge_devices(devices)[:1], out, {"built_at": BUILT_AT})
    assert dict(dump(out)["meta"])["devices"] == "1"
