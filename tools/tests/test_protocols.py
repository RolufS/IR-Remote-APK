import pytest

from irdata.protocols import UnsupportedProtocol, encode_flipper, encode_irdb


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
