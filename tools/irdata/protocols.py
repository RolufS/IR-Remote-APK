"""Encoders: protocol name + numeric fields -> Signal (microsecond pulse sequences)."""
import math

from .signal import Signal, duration_us, normalize, units

NEC_UNIT = 564
JVC_UNIT = 525
_NEC_REPEAT = (16 * NEC_UNIT, 4 * NEC_UNIT, NEC_UNIT)  # (9024, 2256, 564)


class UnsupportedProtocol(Exception):
    pass


def _lsb(value: int, nbits: int) -> list[int]:
    return [(value >> i) & 1 for i in range(nbits)]


def _bytes_lsb(*bytes_: int) -> list[int]:
    return [bit for b in bytes_ for bit in _lsb(b, 8)]


def _pulse_bits(unit_us: int, bits: list[int]) -> list[int]:
    """Signed durations for bits <1,-1|1,-3>, followed by a stop mark."""
    signed: list[int] = []
    for bit in bits:
        signed += [1, -3 if bit else -1]
    return units(unit_us, signed + [1])


def _nec_bits(lead_units: int, bits: list[int], repeat_kind: bool) -> Signal:
    """NEC-style frame: lead (lead_units, -8), bits, stop. repeat_kind: add NEC repeat code."""
    signed = units(NEC_UNIT, [lead_units, -8]) + _pulse_bits(NEC_UNIT, bits)
    return Signal(
        freq=38000,
        frame=normalize(signed),
        repeat=_NEC_REPEAT if repeat_kind else None,
        frame_alt=None,
        period_ms=108,
    )


def _inv(x: int) -> int:
    return x ^ 0xFF


def _biphase(unit_us: int, bits: list[int], one: tuple[int, int], zero: tuple[int, int]) -> list[int]:
    """Signed durations for Manchester-coded bits; one/zero are signed half-bit pairs in units."""
    signed: list[int] = []
    for bit in bits:
        signed += units(unit_us, list(one if bit else zero))
    return signed


def _msb(value: int, nbits: int) -> list[int]:
    return [(value >> i) & 1 for i in range(nbits - 1, -1, -1)]


def _rc5(a: int, c: int) -> Signal:
    c &= 0x7F
    frames = []
    for toggle in (0, 1):
        bits = [1, 0 if c >= 64 else 1, toggle] + _msb(a & 0x1F, 5) + _msb(c & 0x3F, 6)
        frames.append(normalize(_biphase(889, bits, (-1, 1), (1, -1))))
    return Signal(freq=36000, frame=frames[0], repeat=None, frame_alt=frames[1], period_ms=114)


def _rc6(a: int, c: int) -> Signal:
    one, zero = (1, -1), (-1, 1)
    frames = []
    for toggle in (0, 1):
        signed = units(444, [6, -2])
        signed += _biphase(444, [1, 0, 0, 0], one, zero)
        signed += units(444, [2, -2] if toggle else [-2, 2])
        signed += _biphase(444, _msb(a & 0xFF, 8) + _msb(c & 0xFF, 8), one, zero)
        frames.append(normalize(signed))
    return Signal(freq=36000, frame=frames[0], repeat=None, frame_alt=frames[1], period_ms=107)


def _sirc(a: int, c: int, addr_bits: int) -> Signal:
    bits = _lsb(c & 0x7F, 7) + _lsb(a & ((1 << addr_bits) - 1), addr_bits)
    signed = units(600, [4, -1])
    for bit in bits:
        signed += units(600, [2 if bit else 1, -1])
    copy = normalize(signed)
    # copy alternates mark/space starting with a mark, so re-sign it before joining
    signed_copy = [d if i % 2 == 0 else -d for i, d in enumerate(copy)]
    gap = -(45000 - duration_us(copy))
    return Signal(
        freq=40000,
        frame=normalize(signed_copy + [gap] + signed_copy + [gap] + signed_copy),
        repeat=copy,
        frame_alt=None,
        period_ms=45,
    )


def _kaseikyo(*data: int) -> Signal:
    signed = units(432, [8, -4]) + _pulse_bits(432, _bytes_lsb(*data))
    frame = normalize(signed)
    return Signal(
        freq=37000,
        frame=frame,
        repeat=None,
        frame_alt=None,
        period_ms=math.ceil((duration_us(frame) + 74736) / 1000),
    )


def _sharp(d: int, f: int) -> Signal:
    d &= 0x1F
    f &= 0xFF
    half1 = _lsb(d, 5) + _lsb(f, 8) + [1, 0]
    half2 = _lsb(d, 5) + _lsb(f ^ 0xFF, 8) + [0, 1]
    signed: list[int] = []
    for bits, gap in ((half1, [-165]), (half2, [])):
        for bit in bits:
            signed += units(264, [1, -7 if bit else -3])
        signed += units(264, [1] + gap)
    frame = normalize(signed)
    return Signal(
        freq=38000,
        frame=frame,
        repeat=None,
        frame_alt=None,
        period_ms=math.ceil((duration_us(frame) + 43560) / 1000),
    )


def _rca(d: int, f: int) -> Signal:
    d &= 0xF
    f &= 0xFF
    bits = _msb(d, 4) + _msb(f, 8) + _msb(d ^ 0xF, 4) + _msb(f ^ 0xFF, 8)
    signed = units(460, [8, -8])
    for bit in bits:
        signed += units(460, [1, -4 if bit else -2])
    frame = normalize(signed + units(460, [1]))
    return Signal(
        freq=58000,
        frame=frame,
        repeat=None,
        frame_alt=None,
        period_ms=math.ceil((duration_us(frame) + 7360) / 1000),
    )


# --- Flipper ---------------------------------------------------------------

def _f_nec(a: int, c: int) -> Signal:
    return _nec_bits(16, _bytes_lsb(a & 0xFF, _inv(a & 0xFF), c & 0xFF, _inv(c & 0xFF)), True)


def _f_necext(a: int, c: int) -> Signal:
    return _nec_bits(16, _bytes_lsb(a & 0xFF, (a >> 8) & 0xFF, c & 0xFF, (c >> 8) & 0xFF), True)


def _f_samsung32(a: int, c: int) -> Signal:
    return _nec_bits(8, _bytes_lsb(a & 0xFF, a & 0xFF, c & 0xFF, _inv(c & 0xFF)), False)


def _f_nec42(a: int, c: int) -> Signal:
    a &= 0x1FFF
    bits = _lsb(a, 13) + _lsb(a ^ 0x1FFF, 13) + _lsb(c & 0xFF, 8) + _lsb(_inv(c & 0xFF), 8)
    return _nec_bits(16, bits, True)


def _f_nec42ext(a: int, c: int) -> Signal:
    return _nec_bits(16, _lsb(a & 0x3FFFFFF, 26) + _lsb(c & 0xFFFF, 16), True)


def _f_rc5(a: int, c: int) -> Signal:
    return _rc5(a, c)


def _f_rc6(a: int, c: int) -> Signal:
    return _rc6(a, c)


def _f_sirc(n: int):
    def enc(a: int, c: int) -> Signal:
        return _sirc(a, c, n)
    return enc


def _f_kaseikyo(a: int, c: int) -> Signal:
    genre2 = a & 0xF
    genre1 = (a >> 4) & 0xF
    vendor = (a >> 8) & 0xFFFF
    ident = (a >> 24) & 0x3
    vpar = (vendor & 0xF) ^ ((vendor >> 4) & 0xF) ^ ((vendor >> 8) & 0xF) ^ (vendor >> 12)
    b2 = vpar | genre1 << 4
    b3 = genre2 | (c & 0xF) << 4
    b4 = ((c >> 4) & 0x3F) | ident << 6
    return _kaseikyo(vendor & 0xFF, vendor >> 8, b2, b3, b4, b2 ^ b3 ^ b4)


def _f_rca(a: int, c: int) -> Signal:
    return _rca(a, c)


_FLIPPER = {
    "rc5": _f_rc5,
    "rc5x": _f_rc5,
    "rc6": _f_rc6,
    "sirc": _f_sirc(5),
    "sirc15": _f_sirc(8),
    "sirc20": _f_sirc(13),
    "kaseikyo": _f_kaseikyo,
    "rca": _f_rca,
    "nec": _f_nec,
    "necext": _f_necext,
    "samsung32": _f_samsung32,
    "nec42": _f_nec42,
    "nec42ext": _f_nec42ext,
}


def encode_flipper(protocol: str, address: int, command: int) -> Signal:
    fn = _FLIPPER.get(protocol.lower())
    if fn is None:
        raise UnsupportedProtocol(protocol)
    return fn(address, command)


# --- irdb ------------------------------------------------------------------

def _irdb_nec(lead: int, repeat: bool, sub_default_inverse: bool):
    def enc(d: int, s: int, f: int) -> Signal:
        if s >= 0:
            sub = s & 0xFF
        else:
            sub = _inv(d & 0xFF) if sub_default_inverse else d & 0xFF
        bits = _bytes_lsb(d & 0xFF, sub, f & 0xFF, _inv(f & 0xFF))
        return _nec_bits(lead, bits, repeat)
    return enc


def _irdb_jvc(d: int, s: int, f: int) -> Signal:
    bits = _bytes_lsb(d & 0xFF, f & 0xFF)
    body = _pulse_bits(JVC_UNIT, bits)
    return Signal(
        freq=38000,
        frame=normalize(units(JVC_UNIT, [16, -8]) + body),
        repeat=normalize(body),
        frame_alt=None,
        period_ms=60,
    )


def _irdb_sirc(n: int, with_sub: bool):
    def enc(d: int, s: int, f: int) -> Signal:
        a = d | (max(s, 0) << 5) if with_sub else d
        return _sirc(a, f, n)
    return enc


def _irdb_panasonic(d: int, s: int, f: int) -> Signal:
    d, s, f = d & 0xFF, max(s, 0) & 0xFF, f & 0xFF
    return _kaseikyo(0x02, 0x20, d, s, f, d ^ s ^ f)


_IRDB = {
    "rc5": lambda d, s, f: _rc5(d, f),
    "rc6": lambda d, s, f: _rc6(d, f),
    "sony12": _irdb_sirc(5, False),
    "sony15": _irdb_sirc(8, False),
    "sony20": _irdb_sirc(13, True),
    "panasonic": _irdb_panasonic,
    "sharp": lambda d, s, f: _sharp(d, f),
    "rca": lambda d, s, f: _rca(d, f),
    "nec": _irdb_nec(16, True, True),
    "nec1": _irdb_nec(16, True, True),
    "nec2": _irdb_nec(16, False, True),
    "necx1": _irdb_nec(8, False, False),
    "necx2": _irdb_nec(8, False, False),
    "jvc": _irdb_jvc,
}


def encode_irdb(protocol: str, device: int, subdevice: int, function: int) -> Signal:
    fn = _IRDB.get(protocol.lower())
    if fn is None:
        raise UnsupportedProtocol(protocol)
    return fn(device, subdevice, function)


# --- Raw -------------------------------------------------------------------

def _near(value: int, target: int) -> bool:
    return abs(value - target) <= target * 0.25


def _decode_nec_family(data: list[int]) -> Signal | None:
    if len(data) != 67:
        return None
    if _near(data[0], 16 * NEC_UNIT) and _near(data[1], 8 * NEC_UNIT):
        lead, repeat = 16, True
    elif _near(data[0], 8 * NEC_UNIT) and _near(data[1], 8 * NEC_UNIT):
        lead, repeat = 8, False
    else:
        return None
    bits: list[int] = []
    for i in range(32):
        mark, space = data[2 + 2 * i], data[3 + 2 * i]
        if not _near(mark, NEC_UNIT):
            return None
        if _near(space, NEC_UNIT):
            bits.append(0)
        elif _near(space, 3 * NEC_UNIT):
            bits.append(1)
        else:
            return None
    if not _near(data[66], NEC_UNIT):
        return None
    return _nec_bits(lead, bits, repeat)


def from_raw(freq: int, data: list[int]) -> Signal:
    """Canonical NEC/NECx encoding if the timings fit (+-25 %), otherwise the raw sequence."""
    decoded = _decode_nec_family(data)
    if decoded is not None:
        return decoded
    frame = normalize([d if i % 2 == 0 else -d for i, d in enumerate(data)])
    return Signal(
        freq=freq,
        frame=frame,
        repeat=None,
        frame_alt=None,
        period_ms=math.ceil(duration_us(frame) / 1000) + 40,
    )
