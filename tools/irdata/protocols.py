"""Encoders: protocol name + numeric fields -> Signal (microsecond pulse sequences)."""
from .signal import Signal, normalize, units

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


_FLIPPER = {
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


_IRDB = {
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
