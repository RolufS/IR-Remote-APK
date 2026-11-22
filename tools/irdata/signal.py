"""Signal model and pulse-sequence helpers (durations in microseconds)."""
import struct
from dataclasses import dataclass


@dataclass(frozen=True)
class Signal:
    freq: int
    frame: tuple[int, ...]
    repeat: tuple[int, ...] | None  # sent while held; None = resend frame
    frame_alt: tuple[int, ...] | None  # toggle variant (RC5/RC6)
    period_ms: int

    def identity(self) -> tuple[int, ...]:
        """Frame quantized to 50 us steps; frequency is ignored."""
        return tuple(round(d / 50) for d in self.frame)


def normalize(signed: list[int]) -> tuple[int, ...]:
    """Merge same-sign runs, trim leading/trailing gaps; result starts and ends with a mark."""
    merged: list[int] = []
    for d in signed:
        if d == 0:
            continue
        if merged and (merged[-1] > 0) == (d > 0):
            merged[-1] += d
        else:
            merged.append(d)
    while merged and merged[0] < 0:
        merged.pop(0)
    while merged and merged[-1] < 0:
        merged.pop()
    return tuple(abs(d) for d in merged)


def units(unit_us: int, signed_units: list[int]) -> list[int]:
    return [unit_us * u for u in signed_units]


def duration_us(frame: tuple[int, ...]) -> int:
    return sum(frame)


def to_blob(t: tuple[int, ...]) -> bytes:
    return struct.pack(f"<{len(t)}i", *t)


def from_blob(b: bytes) -> tuple[int, ...]:
    return struct.unpack(f"<{len(b) // 4}i", b)
