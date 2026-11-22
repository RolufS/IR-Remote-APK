import pytest

import math

from irdata.protocols import UnsupportedProtocol, encode_flipper, encode_irdb, from_raw


def bits_from_spaces(sig, nbits, offset=2):   # Pause > 1000 us = 1
    sp = sig.frame[offset + 1::2][:nbits]
    return [1 if s > 1000 else 0 for s in sp]


def lsb_bits(*bytes_):
    return [(b >> i) & 1 for b in bytes_ for i in range(8)]


def test_flipper_nec_lg_power():
    s = encode_flipper("NEC", 0x04, 0x08)
    assert s.freq == 38000 and s.period_ms == 108
    assert len(s.frame) == 67 and s.frame[:2] == (9024, 4512)
    assert all(m == 564 for m in s.frame[2::2])
    assert bits_from_spaces(s, 32) == lsb_bits(0x04, 0xFB, 0x08, 0xF7)
    assert s.repeat == (9024, 2256, 564)


def test_samsung32_lead_and_bytes():
    s = encode_flipper("Samsung32", 0x07, 0x02)
    assert s.frame[:2] == (4512, 4512) and s.repeat is None
    assert bits_from_spaces(s, 32) == lsb_bits(0x07, 0x07, 0x02, 0xFD)


def test_necext_uses_raw_16bit_fields():
    s = encode_flipper("NECext", 0xBF40, 0xEF10)
    assert bits_from_spaces(s, 32) == lsb_bits(0x40, 0xBF, 0x10, 0xEF)


def test_flipper_samsung32_equals_irdb_necx2():
    assert encode_flipper("Samsung32", 7, 2) == encode_irdb("NECx2", 7, 7, 2)


def test_flipper_nec_equals_irdb_nec1():
    assert encode_flipper("NEC", 4, 8) == encode_irdb("NEC1", 4, -1, 8)


def test_nec2_has_no_repeat_code():
    assert encode_irdb("NEC2", 4, -1, 8).repeat is None


def test_jvc_frame_and_repeat():
    s = encode_irdb("JVC", 3, -1, 23)
    assert s.frame[:2] == (8400, 4200) and s.repeat[0] == 525 and s.period_ms == 60


def test_unknown_protocol_raises():
    with pytest.raises(UnsupportedProtocol):
        encode_flipper("Pioneer", 1, 2)


def test_nec42_structure():
    s = encode_flipper("NEC42", 0x123, 0x45)
    assert len(s.frame) == 2 + 2 * 42 + 1 and s.frame[:2] == (9024, 4512)
    assert s.repeat == (9024, 2256, 564)


def test_nec42ext_structure():
    s = encode_flipper("NEC42ext", 0x1234567, 0xABCD)
    assert len(s.frame) == 2 + 2 * 42 + 1 and s.frame[:2] == (9024, 4512)


def test_rc5_philips_power_exact():
    s = encode_flipper("RC5", 0x00, 0x0C)
    u = [1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 2, 1, 1]
    assert s.frame == tuple(x * 889 for x in u) and s.freq == 36000
    assert s.frame_alt is not None and s.frame_alt != s.frame


def test_rc6_header_and_toggle():
    s = encode_flipper("RC6", 0x00, 0x0C)
    assert s.frame[:2] == (2664, 888) and s.frame != s.frame_alt


@pytest.mark.parametrize("fl, ir", [
    (("RC6", 0x00, 0x0C), ("RC6", 0, -1, 12)),
    (("RC5", 0x00, 0x0C), ("RC5", 0, -1, 12)),
    (("SIRC", 0x01, 0x15), ("Sony12", 1, -1, 21)),
    (("SIRC20", 0x0E5A, 0x19), ("Sony20", 0x1A, 0x72, 0x19)),
    (("Kaseikyo", 0x00200280, 0x3D0), ("Panasonic", 128, 0, 61)),
    (("Kaseikyo", 0x02200289, 0x2B0), ("Panasonic", 128, 9, 171)),
    (("RCA", 0xA, 0x55), ("RCA", 0xA, -1, 0x55)),
])
def test_flipper_and_irdb_agree(fl, ir):
    assert encode_flipper(*fl) == encode_irdb(*ir)


def test_sony_sends_three_copies():
    s = encode_flipper("SIRC", 0x01, 0x15)
    assert s.frame.count(2400) == 3 and s.repeat.count(2400) == 1 and s.period_ms == 45


def test_panasonic_bytes():
    s = encode_irdb("Panasonic", 128, 0, 61)
    assert s.freq == 37000 and bits_from_spaces(s, 48) == lsb_bits(0x02, 0x20, 0x80, 0x00, 0x3D, 0x80 ^ 0x3D)


def test_sharp_frame_and_period():
    s = encode_irdb("Sharp", 1, -1, 0x10)
    assert s.freq == 38000 and s.frame[:2] == (264, 1848)
    assert s.period_ms == math.ceil((sum(s.frame) + 43560) / 1000)


def test_rca_frame_and_period():
    s = encode_irdb("RCA", 0xA, -1, 0x55)
    assert s.freq == 58000 and s.frame[:3] == (3680, 3680, 460) and s.frame[3] == 4 * 460
    assert s.period_ms == math.ceil((sum(s.frame) + 7360) / 1000)


def test_raw_nec_is_canonicalized():
    ref = encode_flipper("NEC", 4, 8)
    jitter = [int(d * (1.1 if i % 3 else 0.92)) for i, d in enumerate(ref.frame)]
    assert from_raw(38000, jitter) == ref


def test_raw_unknown_stays_raw():
    s = from_raw(38000, [3000, 1000, 500, 500, 500])
    assert s.frame == (3000, 1000, 500, 500, 500) and s.repeat is None and s.period_ms == 6 + 40


def test_rc5x_has_no_field_offset_in_flipper():
    assert encode_flipper("RC5X", 0, 0x0C) == encode_flipper("RC5", 0, 0x4C)
    assert encode_flipper("RC5X", 0, 0x0C).frame != encode_flipper("RC5", 0, 0x0C).frame


def test_rc6_exact_units():
    s = encode_flipper("RC6", 0x00, 0x0C)
    u = [6, 2, 1, 2, 1, 1, 1, 1, 1, 2, 2] + [1, 1] * 11 + [1, 2, 1, 1, 2, 1, 1, 1]
    assert s.frame == tuple(x * 444 for x in u)


def test_raw_nec_trailing_gap_and_repeat_burst():
    ref = encode_flipper("NEC", 4, 8)
    assert from_raw(38000, list(ref.frame) + [40000]) == ref
    assert from_raw(38000, list(ref.frame) + [40000, 9024, 2256, 564]) == ref
    assert from_raw(38000, list(ref.frame) + [40000, 9024, 2256, 564, 96000]) == ref
    assert from_raw(38000, list(ref.frame) + [40000] + list(ref.frame)) == ref
    assert from_raw(38000, list(ref.frame) + [500, 9024, 2256, 564]).repeat is None


def test_raw_necx_path():
    ref = encode_flipper("Samsung32", 7, 2)
    jitter = [int(d * (1.1 if i % 3 else 0.92)) for i, d in enumerate(ref.frame)]
    assert from_raw(38000, jitter) == ref and ref.repeat is None
