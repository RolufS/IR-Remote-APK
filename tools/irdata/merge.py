"""Fasst Fernbedienungs-Dateien zu Code-Saetzen, Eingangs-Varianten und Suchlauf zusammen."""
from collections import defaultdict
from dataclasses import dataclass

from .brands import family_members
from .keys import BASIC_KEYS, HDMI_KEYS
from .signal import Signal
from .sources import RemoteFile

_POWER_SLOTS = {"POWER", "POWER_ON", "POWER_OFF"}


@dataclass
class CodeSet:
    keys: dict[str, Signal]  # ohne HDMI1-4
    support: int
    brand_support: dict[str, int]


@dataclass
class InputVariant:
    keys: dict[str, Signal]
    support: int


@dataclass
class MergeResult:
    code_sets: list[CodeSet]
    brand_sets: dict[str, list[tuple[int, bool]]]
    variants: list[InputVariant]
    variant_order: dict[tuple[int, str], list[int]]
    scan: list[tuple[int, str, Signal]]
    dropped_files: int


def _basic_count(keys: dict[str, Signal]) -> int:
    return len({"POWER" if k in _POWER_SLOTS else k for k in keys} & BASIC_KEYS)


def _has_variant(f: RemoteFile) -> bool:
    return "HDMI1" in f.keys and "HDMI2" in f.keys


def _signature(keys: dict[str, Signal]) -> tuple:
    power = "POWER" if "POWER" in keys else "POWER_ON" if "POWER_ON" in keys else None
    if power and "MUTE" in keys:
        return (keys[power].identity(), keys["MUTE"].identity())
    return tuple(sorted((k, s.identity()) for k, s in keys.items() if k in BASIC_KEYS))


def _file_rank(f: RemoteFile) -> tuple:
    return (f.source != "flipper", f.path)


def _merge_keys(files: list[RemoteFile]) -> dict[str, Signal]:
    """Pro Taste: meiste Dateien, dann Flipper vor irdb, dann kleinster Pfad."""
    votes: dict[str, dict[tuple, list[RemoteFile]]] = defaultdict(lambda: defaultdict(list))
    for f in files:
        for k, s in f.keys.items():
            if k not in HDMI_KEYS:
                votes[k][s.identity()].append(f)
    result: dict[str, Signal] = {}
    for k in sorted(votes):
        best = min(
            (min(fs, key=_file_rank) for fs in votes[k].values()),
            key=lambda rep: (-len(votes[k][rep.keys[k].identity()]), *_file_rank(rep)),
        )
        result[k] = best.keys[k]
    return result


def _variant_id(keys: dict[str, Signal]) -> tuple:
    return tuple(keys[h].identity() if h in keys else None for h in HDMI_KEYS)


def merge(files: list[RemoteFile]) -> MergeResult:
    full: list[RemoteFile] = []
    input_only: list[RemoteFile] = []
    dropped = 0
    for f in files:
        if _basic_count(f.keys) >= 3:
            full.append(f)
        elif _has_variant(f):
            input_only.append(f)
        else:
            dropped += 1

    # Code-Saetze
    groups: dict[tuple, list[RemoteFile]] = defaultdict(list)
    for f in full:
        groups[_signature(f.keys)].append(f)

    def set_order(item):
        sig, fs = item
        return (-len(fs), min(f.brand for f in fs), repr(sig))

    ordered = sorted(groups.items(), key=set_order)
    code_sets: list[CodeSet] = []
    file_set: dict[int, int] = {}  # id(RemoteFile) -> Code-Satz-Index
    for i, (_, fs) in enumerate(ordered):
        brand_support: dict[str, int] = {}
        for f in fs:
            brand_support[f.brand] = brand_support.get(f.brand, 0) + 1
            file_set[id(f)] = i
        code_sets.append(CodeSet(_merge_keys(fs), len(fs), brand_support))

    # Markenlisten
    owned: dict[str, list[int]] = defaultdict(list)
    for i, cs in enumerate(code_sets):
        for b in cs.brand_support:
            owned[b].append(i)
    candidates = set(owned)
    for b in owned:
        candidates |= family_members(b)
    brand_sets: dict[str, list[tuple[int, bool]]] = {}
    for b in sorted(candidates):
        own = sorted(owned.get(b, []), key=lambda i: (-code_sets[i].brand_support[b], i))
        family = sorted({i for m in family_members(b) for i in owned.get(m, [])} - set(own))
        entries = [(i, False) for i in own] + [(i, True) for i in family]
        if entries:
            brand_sets[b] = entries

    # Varianten
    variant_files: dict[tuple, list[RemoteFile]] = defaultdict(list)
    for f in full + input_only:
        if _has_variant(f):
            variant_files[_variant_id(f.keys)].append(f)
    ordered_variants = sorted(
        variant_files.values(), key=lambda fs: (-len(fs), min(_file_rank(f) for f in fs))
    )
    variants: list[InputVariant] = []
    own_v: dict[int, set[int]] = defaultdict(set)
    attached_v: dict[int, set[int]] = defaultdict(set)
    brand_v: dict[str, set[int]] = defaultdict(set)
    for vi, fs in enumerate(ordered_variants):
        rep = min(fs, key=_file_rank)
        variants.append(InputVariant({h: rep.keys[h] for h in HDMI_KEYS if h in rep.keys}, len(fs)))
        for f in fs:
            brand_v[f.brand].add(vi)
            if id(f) in file_set:
                own_v[file_set[id(f)]].add(vi)
            else:
                ids = {s.identity() for s in f.keys.values()}
                for ci in owned.get(f.brand, []):
                    if ids & {s.identity() for s in code_sets[ci].keys.values()}:
                        attached_v[ci].add(vi)

    def by_support(vs: set[int]) -> list[int]:
        return sorted(vs, key=lambda v: (-variants[v].support, v))

    variant_order: dict[tuple[int, str], list[int]] = {}
    for b, entries in brand_sets.items():
        for ci, _ in entries:
            seen: list[int] = []
            for group in (own_v[ci], attached_v[ci], brand_v[b]):
                seen += [v for v in by_support(group) if v not in seen]
            variant_order[(ci, b)] = seen

    # Suchlauf
    scan: list[tuple[int, str, Signal]] = []
    used: set[tuple] = set()
    for ci, cs in enumerate(code_sets):
        sig = cs.keys.get("POWER_OFF") or cs.keys.get("POWER")
        if sig is None or sig.identity() in used:
            continue
        used.add(sig.identity())
        label = min(cs.brand_support, key=lambda b: (-cs.brand_support[b], b))
        scan.append((ci, label, sig))

    return MergeResult(code_sets, brand_sets, variants, variant_order, scan, dropped)


def coverage(files: list[RemoteFile]) -> float:
    """Anteil sendbarer Dateien (>= 3 Grundtasten in keys) unter den geeigneten (basic_named >= 3)."""
    eligible = [f for f in files if f.basic_named >= 3]
    if not eligible:
        return 0.0
    return sum(_basic_count(f.keys) >= 3 for f in eligible) / len(eligible)
