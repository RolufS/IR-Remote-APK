"""Markennamen, Hotelmarken und Geraete-Familien."""
import re

_OVERRIDES = {k.lower(): k for k in ("LG", "TCL", "JVC", "RCA", "AOC", "NEC", "BBK", "JMB")}

HOTEL_BRANDS = [
    "Samsung", "LG", "Philips", "Sony", "Panasonic", "Hisense", "TCL", "Telefunken",
    "Grundig", "Toshiba", "Sharp", "Thomson", "Loewe", "Medion", "Vestel",
]

FAMILIES = {
    "Vestel": ["Telefunken", "Finlux", "JVC", "Hitachi", "Toshiba", "Bush", "Techwood",
               "Digihome", "Luxor", "Polaroid"],
    "TCL": ["Thomson"],
    "Grundig": ["Beko", "Arcelik"],
}


def _key(raw: str) -> str:
    return re.sub(r"[^a-z0-9]", "", raw.lower())


def canonical_brand(raw: str) -> str:
    key = _key(raw)
    if key in _OVERRIDES:
        return _OVERRIDES[key]
    if raw.isupper():
        return raw.title()
    return raw


def family_members(brand: str) -> set[str]:
    key = _key(brand)
    result: set[str] = set()
    for head, members in FAMILIES.items():
        group = [head, *members]
        if key in {_key(g) for g in group}:
            result.update(g for g in group if _key(g) != key)
    return result
