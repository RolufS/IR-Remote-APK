from irdata.signal import Signal, normalize, units, from_blob, to_blob


def test_normalize_merges_and_trims():
    assert normalize([-500, 100, 200, -300, -100, 50, -9000]) == (300, 400, 50)


def test_normalize_empty_is_empty():
    assert normalize([-5, -6]) == ()


def test_units_scales_with_sign():
    assert units(889, [1, -2]) == [889, -1778]


def test_identity_ignores_small_jitter_and_freq():
    a = Signal(38000, (9024, 4512, 564), None, None, 108)
    b = Signal(36000, (9010, 4520, 560), None, None, 108)
    assert a.identity() == b.identity()


def test_blob_roundtrip():
    t = (9024, 4512, 564, 70000)
    assert from_blob(to_blob(t)) == t and len(to_blob(t)) == 16
