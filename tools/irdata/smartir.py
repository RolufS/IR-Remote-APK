"""Liest SmartIR-Klimadateien (codes/climate/*.json) in AcDevice-Objekte.

Pulsfolgen sind Mikrosekunden, abwechselnd an/aus, beginnend und endend mit "an".
Unbrauchbares wird nicht still verworfen, sondern je Grund in `Stats` gezaehlt.
"""
import base64
import binascii
import json
import re
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from .signal import normalize

GAP_CAP_US = 100_000  # laengste Pause innerhalb eines Musters
MAX_PATTERN_US = 1_500_000  # laengstes Muster
BROADLINK_IR = 0x26
_TERMINATOR = b"\x00\x0d\x05"
COOL_MODE = "cool"

# Skip-Gruende (Statistik im Build-Log)
F_JSON = "kaputtes JSON"
F_FORMAT = "Format nicht sicher dekodierbar"
F_NO_OFF = "kein gueltiges off"
F_NO_COOL = "kein gueltiges Kuehl-Kommando"
C_BASE64 = "Base64 ungueltig"
C_NOT_IR = "kein Broadlink-IR-Paket"
C_TRUNCATED = "Paket abgeschnitten"
C_ZERO = "Dauer 0 im Muster"
C_EMPTY = "leer"
C_TOO_LONG = "Muster > 1,5 s"
C_RAW = "Raw ungueltig"
C_PATH = "Pfad passt nicht zu mode/fan[/swing]/temp"
C_EXTRA = "Zusatzkommando (nicht off)"


class CommandError(ValueError):
    """Kommando nicht sicher dekodierbar; `reason` ist einer der C_*-Gruende."""

    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


@dataclass
class AcDevice:
    brand: str
    models: list[str]
    sources: list[str]
    min_temp: float
    max_temp: float
    precision: float
    modes: list[str]
    fan_modes: list[str]
    swing_modes: list[str]
    commands: dict[str, tuple[int, ...]]  # "off", "cool/auto/22", "cool/auto/off/22"


@dataclass
class Stats:
    files: int = 0
    skipped_files: Counter = field(default_factory=Counter)
    skipped_commands: Counter = field(default_factory=Counter)


# ---------------------------------------------------------------- Dekodierung

def decode_base64(text: str) -> bytes:
    """Tolerant gegenueber Leerraum, fehlendem/ueberzaehligem Padding und Muell nach dem Padding.

    Alles ab dem ersten '=' wird ignoriert (Padding beendet die Daten); ein einzelnes
    ueberzaehliges Zeichen (Laenge % 4 == 1) traegt kein ganzes Byte und entfaellt.
    Andere Fremdzeichen machen das Kommando mehrdeutig -> CommandError."""
    s = re.sub(r"\s", "", text)
    if not s or re.search(r"[^A-Za-z0-9+/=]", s):
        raise CommandError(C_BASE64)
    data = s.split("=", 1)[0]
    if len(data) % 4 == 1:
        data = data[:-1]
    try:
        return base64.b64decode(data + "=" * (-len(data) % 4), validate=True)
    except binascii.Error as e:
        raise CommandError(C_BASE64) from e


def broadlink_pulses(raw: bytes) -> tuple[int, ...]:
    """Broadlink-Paket -> fertiges Muster (Wiederholungen angewendet, Pausen gekappt)."""
    if len(raw) < 4 or raw[0] != BROADLINK_IR:
        raise CommandError(C_NOT_IR)
    repeats = raw[1]
    length = raw[2] | raw[3] << 8
    payload = raw[4:4 + length]
    if len(payload) < length:
        # Laengenfeld groesser als die Daten: nur sicher, wenn das Endezeichen da ist.
        end = payload.find(_TERMINATOR)
        if end < 0:
            raise CommandError(C_TRUNCATED)
        payload = payload[:end + len(_TERMINATOR)]
    units: list[int] = []
    i = 0
    while i < len(payload):
        if payload[i] == 0:
            if i + 2 >= len(payload):
                break  # Fuellbytes am Ende
            units.append(payload[i + 1] << 8 | payload[i + 2])
            i += 3
        else:
            units.append(payload[i])
            i += 1
    while units and units[-1] == 0:
        units.pop()  # Fuellbytes
    if 0 in units:
        raise CommandError(C_ZERO)
    if len(units) % 2 and units[-1] == 0x0D05:
        units.pop()  # Endezeichen an "an"-Position (ungerade Pulszahl davor) ist keine 109-ms-Dauer
    return finish([int(u * 269000 / 8192 + 0.5) for u in units], repeats)


def raw_pulses(value) -> tuple[int, ...]:
    """ESPHome/LOOKin-Raw: vorzeichenbehaftete µs (+ an, - aus) als Liste, JSON-Text oder
    durch Leerzeichen/Kommas getrennter Text. Aufeinanderfolgende Werte gleichen Vorzeichens
    werden addiert (wie irdata.signal.normalize), fuehrende und abschliessende Pausen entfallen."""
    if isinstance(value, str):
        text = value.strip()
        try:
            value = json.loads(text) if text.startswith("[") else [int(t) for t in re.split(r"[\s,]+", text) if t]
        except ValueError as e:
            raise CommandError(C_RAW) from e
    if not isinstance(value, list) or not all(isinstance(v, int) and not isinstance(v, bool) for v in value):
        raise CommandError(C_RAW)
    if 0 in value:
        raise CommandError(C_ZERO)
    return finish(list(normalize(value)), 0)


def finish(durations: list[int], repeats: int) -> tuple[int, ...]:
    """Wiederholt das Muster (`repeats` zusaetzliche Male), kappt Pausen auf 100 ms,
    entfernt die letzte Pause und prueft die Gesamtlaenge."""
    frame = list(durations)
    if repeats and len(frame) % 2:
        frame.append(GAP_CAP_US)  # Muster endet mit "an": Pause zwischen den Wiederholungen
    out = frame * (repeats + 1)
    for i in range(1, len(out), 2):
        out[i] = min(out[i], GAP_CAP_US)
    if len(out) % 2 == 0 and out:
        out.pop()
    if not out:
        raise CommandError(C_EMPTY)
    if sum(out) > MAX_PATTERN_US:
        raise CommandError(C_TOO_LONG)
    return tuple(out)


# ---------------------------------------------------------------- Marken

_BRAND_ALIASES = {
    "ggeneralelectric": "General Electric",
    "hyndai": "Hyundai",
    "dalkin": "Daikin",
    "mitsubishiheavyindustries": "Mitsubishi Heavy",
    "mitsubishielectricstarmex": "Mitsubishi Electric",
    "senvillefnativerg66a2bgefu1": "Senville",
}
_ACRONYMS = {k.lower(): k for k in ("AEG", "AUX", "ECA", "HTW", "IGC", "LG", "TCL", "VS")}


def brand_key(raw: str) -> str:
    return re.sub(r"[^a-z0-9]", "", raw.lower())


def normalize_brand(raw: str) -> str:
    """Leerraum, bekannte Tippfehler, Grossschreibung ("FUJITSU" -> "Fujitsu", "LG" bleibt)."""
    name = " ".join(raw.split())
    key = brand_key(name)
    if key in _BRAND_ALIASES:
        return _BRAND_ALIASES[key]
    if key in _ACRONYMS:
        return _ACRONYMS[key]
    if name.isupper():
        return name.title()
    return name


def unify_brand_case(devices: list[AcDevice]) -> None:
    """Gleiche Marke in verschiedener Schreibweise ("DeLonghi"/"Delonghi"): haeufigste gewinnt."""
    spellings: dict[str, Counter] = {}
    for d in devices:
        spellings.setdefault(brand_key(d.brand), Counter())[d.brand] += 1
    for d in devices:
        counts = spellings[brand_key(d.brand)]
        d.brand = min(counts, key=lambda s: (-counts[s], s))


# ---------------------------------------------------------------- Dateien

def _temp_key(raw: str) -> str | None:
    try:
        t = float(raw)
    except ValueError:
        return None
    if t != t or t in (float("inf"), float("-inf")):
        return None
    return str(int(t)) if t.is_integer() else repr(t)


def _leaves(node, path=()):
    if isinstance(node, dict):
        for k, v in node.items():
            yield from _leaves(v, (*path, str(k)))
    else:
        yield path, node


def _ordered(listed, used: list[str]) -> list[str]:
    """Liste aus der Datei, beschraenkt auf tatsaechlich benutzte Werte, Unbekanntes hinten an."""
    listed = [str(x) for x in listed] if isinstance(listed, list) else []
    return [x for x in listed if x in used] + [x for x in used if x not in listed]


def read_file(path: Path, stats: Stats) -> AcDevice | None:
    stats.files += 1
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        brand = normalize_brand(str(data["manufacturer"]))
        commands_in = data["commands"]
        if not isinstance(commands_in, dict):
            raise TypeError
    except (ValueError, KeyError, TypeError):
        stats.skipped_files[F_JSON] += 1
        return None
    controller = str(data.get("supportedController", ""))
    encoding = str(data.get("commandsEncoding", ""))
    if encoding == "Base64" and controller == "Broadlink":
        def decode(v):
            if not isinstance(v, str):
                raise CommandError(C_BASE64)
            return broadlink_pulses(decode_base64(v))
    elif encoding == "Raw" and controller in ("ESPHome", "LOOKin"):
        decode = raw_pulses
    else:
        stats.skipped_files[F_FORMAT] += 1
        stats.skipped_files[f"{F_FORMAT}: {controller}/{encoding}"] += 1
        return None

    swing = "swingModes" in data
    depth = 4 if swing else 3
    commands: dict[str, tuple[int, ...]] = {}
    for p, value in _leaves(commands_in):
        if any(seg.startswith(("$", "_")) for seg in p):
            continue  # Kommentare
        if len(p) == 1:
            if p[0] != "off":
                stats.skipped_commands[C_EXTRA] += 1
                continue
            key = "off"
        else:
            temp = _temp_key(p[-1]) if len(p) == depth else None
            if temp is None or any("/" in seg for seg in p):
                stats.skipped_commands[C_PATH] += 1
                continue
            key = "/".join((*p[:-1], temp))
        if value is None or (isinstance(value, str) and not value.strip()):
            stats.skipped_commands[C_EMPTY] += 1
            continue
        try:
            pulses = decode(value) if isinstance(value, (str, list)) else None
        except CommandError as e:
            stats.skipped_commands[e.reason] += 1
            continue
        if pulses is None:
            stats.skipped_commands[C_RAW] += 1
            continue
        commands.setdefault(key, pulses)

    paths = [k.split("/") for k in commands if k != "off"]
    modes = list(dict.fromkeys(p[0] for p in paths))
    if COOL_MODE not in modes and "cold" in modes:
        commands = {_rename_mode(k, "cold", COOL_MODE): v for k, v in commands.items()}
        data["operationModes"] = [COOL_MODE if m == "cold" else m for m in data.get("operationModes") or []]
        paths = [k.split("/") for k in commands if k != "off"]
        modes = list(dict.fromkeys(p[0] for p in paths))
    if "off" not in commands:
        stats.skipped_files[F_NO_OFF] += 1
        return None
    if COOL_MODE not in modes:
        stats.skipped_files[F_NO_COOL] += 1
        return None

    models = data.get("supportedModels") or []
    try:
        min_temp = float(data["minTemperature"])
        max_temp = float(data["maxTemperature"])
        precision = float(data.get("precision") or 1)
    except (KeyError, TypeError, ValueError):
        stats.skipped_files[F_JSON] += 1
        return None
    return AcDevice(
        brand=brand,
        models=[str(m) for m in models] if isinstance(models, list) else [str(models)],
        sources=[Path(path).name],
        min_temp=min_temp,
        max_temp=max_temp,
        precision=precision,
        modes=_ordered(data.get("operationModes"), modes),
        fan_modes=_ordered(data.get("fanModes"), list(dict.fromkeys(p[1] for p in paths))),
        swing_modes=_ordered(data.get("swingModes"), list(dict.fromkeys(p[2] for p in paths))) if swing else [],
        commands=commands,
    )


def _rename_mode(key: str, old: str, new: str) -> str:
    parts = key.split("/")
    if parts[0] == old and len(parts) > 1:
        parts[0] = new
    return "/".join(parts)


def read_all(directory: Path, stats: Stats) -> list[AcDevice]:
    devices = [d for f in sorted(Path(directory).glob("*.json"), key=lambda f: f.name)
               if (d := read_file(f, stats)) is not None]
    unify_brand_case(devices)
    return devices


def merge_devices(devices: list[AcDevice]) -> list[AcDevice]:
    """Fasst Geraete derselben Marke mit identischen Kommandos zusammen (Modelle/Quellen vereinigt)."""
    merged: dict[tuple, AcDevice] = {}
    for d in devices:
        key = (d.brand, tuple(sorted(d.commands.items())))
        if key not in merged:
            merged[key] = AcDevice(d.brand, list(d.models), list(d.sources), d.min_temp, d.max_temp,
                                   d.precision, list(d.modes), list(d.fan_modes), list(d.swing_modes),
                                   dict(d.commands))
            continue
        m = merged[key]
        m.models += [x for x in d.models if x not in m.models]
        m.sources += [x for x in d.sources if x not in m.sources]
        m.min_temp = min(m.min_temp, d.min_temp)
        m.max_temp = max(m.max_temp, d.max_temp)
        for attr in ("modes", "fan_modes", "swing_modes"):
            lst = getattr(m, attr)
            lst += [x for x in getattr(d, attr) if x not in lst]
    return list(merged.values())
