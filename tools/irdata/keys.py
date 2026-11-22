"""Normalisiert Tastennamen aus Flipper-IRDB und irdb auf das kanonische Vokabular."""
import re

BASIC_KEYS = frozenset({"POWER", "MUTE", "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN"})
HDMI_KEYS = ("HDMI1", "HDMI2", "HDMI3", "HDMI4")

_CH = r"(?:CH|CHAN|CHANNEL|PROG|P)"


def _dir(name: str) -> str:
    return (
        rf"{name}|CURSOR {name}|ARROW {name}|{name} ARROW|ARW {name}|NAV {name}"
    )


# Reihenfolge ist wichtig: spezifischere Muster vor allgemeineren.
_RULES = [
    ("POWER_ON", r"POWER ON|PWR ON|ON|DISCRETE ON"),
    ("POWER_OFF", r"POWER OFF|PWR OFF|OFF|DISCRETE OFF"),
    ("POWER", r"POWER|POWER TOGGLE|ON OFF|ONOFF|STANDBY|PWR"),
    ("MUTE", r"MUTE|MUTING"),
    ("VOL_UP", r"(?:VOL|VOLUME) ?(?:UP|PLUS)|VOLUP"),
    ("VOL_DOWN", r"(?:VOL|VOLUME) ?(?:DOWN|DN|MINUS)|VOLDN|VOLDOWN"),
    ("CH_UP", rf"{_CH} ?(?:UP|PLUS|NEXT)"),
    ("CH_DOWN", rf"{_CH} ?(?:DOWN|DN|MINUS|PREV|PREVIOUS)"),
    *[(f"DIGIT_{n}", rf"(?:DIGIT |NUM |KEY )?{n}") for n in range(10)],
    *[(f"HDMI{n}", rf"(?:(?:INPUT|SOURCE) )?HDMI ?{n}(?: INPUT)?") for n in range(1, 5)],
    ("SOURCE", r"SOURCE|INPUT|INPUT SOURCE|AV|TV AV|TV/AV|INPUT SELECT|EXT"),
    ("MENU", r"MENU|SETTINGS|SETUP"),
    ("HOME", r"HOME|SMART HUB|SMARTHUB|SMART"),
    ("OK", r"OK|ENTER|SELECT|CONFIRM"),
    ("UP", _dir("UP")),
    ("DOWN", _dir("DOWN")),
    ("LEFT", _dir("LEFT")),
    ("RIGHT", _dir("RIGHT")),
    ("BACK", r"BACK|RETURN|RET"),
    ("EXIT", r"EXIT|CLOSE"),
    ("INFO", r"INFO|DISPLAY|INFORMATION"),
    ("LIST", r"LIST|CH LIST|CHANNEL LIST"),
    ("PRE_CH", r"PRE CH|PRECH|LAST|PREV CH|RECALL|FLASHBACK|Q VIEW"),
    *[(c, rf"{c}(?: KEY| BUTTON)?") for c in ("RED", "GREEN", "YELLOW", "BLUE")],
]
_COMPILED = [(name, re.compile(pat)) for name, pat in _RULES]
_EXTRA_OK = re.compile(r"[A-Z0-9 ]{1,20}")


def _normalize(raw: str) -> str:
    s = raw.upper().replace("+", " PLUS")
    s = re.sub(r"-$", " MINUS", s.strip())
    s = re.sub(r"[_.\-]", " ", s)
    return re.sub(r" +", " ", s).strip()


def canonical_key(raw_name: str) -> str | None:
    """Kanonischer Tastenname, `X:<Label>` fuer Extras oder None (verwerfen)."""
    name = _normalize(raw_name)
    for canon, rx in _COMPILED:
        if rx.fullmatch(name):
            return canon
    if _EXTRA_OK.fullmatch(name):
        return "X:" + name.title()
    return None
