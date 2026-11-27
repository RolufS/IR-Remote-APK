"""Kommando-Blob einer Klimaanlage (Vertrag zur App: de.hotelremote.data.AcCodes).

Inhalt vor zlib (alle Zahlen little-endian):

    u8   Version (1)
    u16  Anzahl Symbole N (1..256)
    N x  u32 Dauer in µs (Symboltabelle)
    u16  Anzahl Kommandos M
    M x  u8 Pfadlaenge in Bytes, Pfad (UTF-8, z. B. "off", "cool/auto/22", "cool/auto/off/22"),
         u16 Anzahl Pulse P, P x u8 Symbolindex

Pulse abwechselnd an/aus, beginnend und endend mit "an"; Traeger 38 kHz.
Das Ganze ist mit zlib (RFC 1950, wie java.util.zip.Inflater) komprimiert.
Die Symboltabelle fasst je Geraet aehnliche Dauern zusammen: jede Originaldauer liegt
innerhalb von ±10 % ihres Symbols.
"""
import struct
import zlib

VERSION = 1
TOLERANCE = 0.10
_SPAN = 1.2  # groesste/kleinste Dauer eines Clusters; Mittelpunkt liegt dann in ±10 %


class BlobError(ValueError):
    pass


def clusters(durations) -> dict[int, int]:
    """Dauer -> Symbolwert. Gierig aufsteigend: ein Cluster umfasst Werte bis 1,2 x seinem
    kleinsten Wert; Symbol = haeufigkeitsgewichteter Mittelwert, auf ±10 % aller Mitglieder begrenzt."""
    counts: dict[int, int] = {}
    for d in durations:
        counts[d] = counts.get(d, 0) + 1
    values = sorted(counts)
    result: dict[int, int] = {}
    i = 0
    while i < len(values):
        j = i
        while j + 1 < len(values) and values[j + 1] <= values[i] * _SPAN:
            j += 1
        members = values[i:j + 1]
        lo, hi = members[0], members[-1]
        total = sum(counts[v] for v in members)
        mean = round(sum(v * counts[v] for v in members) / total)
        low_bound = -(-hi * 10 // 11)  # ceil(hi / 1.1)
        high_bound = lo * 11 // 10  # floor(lo * 1.1)
        rep = min(max(mean, low_bound), high_bound)
        for v in members:
            result[v] = rep
        i = j + 1
    return result


def encode(commands: dict[str, tuple[int, ...]]) -> bytes:
    mapping = clusters(d for pulses in commands.values() for d in pulses)
    symbols = sorted(set(mapping.values()))
    if not 1 <= len(symbols) <= 256:
        raise BlobError(f"{len(symbols)} Symbole")
    index = {s: i for i, s in enumerate(symbols)}
    out = bytearray(struct.pack("<BH", VERSION, len(symbols)))
    out += struct.pack(f"<{len(symbols)}I", *symbols)
    out += struct.pack("<H", len(commands))
    for path, pulses in commands.items():
        p = path.encode("utf-8")
        if len(p) > 255 or len(pulses) > 0xFFFF:
            raise BlobError(f"Kommando zu gross: {path}")
        out += struct.pack("<B", len(p)) + p + struct.pack("<H", len(pulses))
        out += bytes(index[mapping[d]] for d in pulses)
    return zlib.compress(bytes(out), 9)


def decode(blob: bytes) -> dict[str, tuple[int, ...]]:
    raw = zlib.decompress(blob)
    version, n = struct.unpack_from("<BH", raw, 0)
    if version != VERSION:
        raise BlobError(f"Version {version}")
    pos = 3
    symbols = struct.unpack_from(f"<{n}I", raw, pos)
    pos += 4 * n
    (m,) = struct.unpack_from("<H", raw, pos)
    pos += 2
    commands: dict[str, tuple[int, ...]] = {}
    for _ in range(m):
        plen = raw[pos]
        path = raw[pos + 1:pos + 1 + plen].decode("utf-8")
        pos += 1 + plen
        (count,) = struct.unpack_from("<H", raw, pos)
        pos += 2
        commands[path] = tuple(symbols[i] for i in raw[pos:pos + count])
        pos += count
    if pos != len(raw):
        raise BlobError("Restbytes")
    return commands
