import pytest

from irdata.keys import BASIC_KEYS, HDMI_KEYS, canonical_key


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("Power", "POWER"),
        ("On_off", "POWER"),
        ("Vol_up", "VOL_UP"),
        ("Vol_dn", "VOL_DOWN"),
        ("VOLUME +", "VOL_UP"),
        ("VOLUME -", "VOL_DOWN"),
        ("Ch_next", "CH_UP"),
        ("Ch_prev", "CH_DOWN"),
        ("CHANNEL -", "CH_DOWN"),
        ("1", "DIGIT_1"),
        ("DIGIT 0", "DIGIT_0"),
        ("INPUT SOURCE", "SOURCE"),
        ("Input", "SOURCE"),
        ("Tv_av", "SOURCE"),
        ("HDMI1", "HDMI1"),
        ("HDMI 2", "HDMI2"),
        ("Input_HDMI3", "HDMI3"),
        ("HDMI5", "X:Hdmi5"),
        ("Arw_up", "UP"),
        ("Enter", "OK"),
        ("Return", "BACK"),
        ("Red", "RED"),
        ("Netflix", "X:Netflix"),
        ("SD CARD / USB / MEDIA PLAYER", None),
        ("Power_on", "POWER_ON"),
        ("Off", "POWER_OFF"),
        ("Cursor down", "DOWN"),
        ("Nav_left", "LEFT"),
        ("Right arrow", "RIGHT"),
        ("Smart hub", "HOME"),
        ("Pre_ch", "PRE_CH"),
        ("Channel list", "LIST"),
        ("Green key", "GREEN"),
    ],
)
def test_canonical_key(raw, expected):
    assert canonical_key(raw) == expected


def test_constants():
    assert BASIC_KEYS == frozenset(
        {"POWER", "MUTE", "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN"}
    )
    assert HDMI_KEYS == ("HDMI1", "HDMI2", "HDMI3", "HDMI4")
