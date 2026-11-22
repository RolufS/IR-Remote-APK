package de.hotelremote

/** Kanonische Tastennamen. Extras sind als "X:<Label>" kodiert. */
object Keys {
    const val POWER = "POWER"
    const val POWER_ON = "POWER_ON"
    const val POWER_OFF = "POWER_OFF"
    const val MUTE = "MUTE"
    const val VOL_UP = "VOL_UP"
    const val VOL_DOWN = "VOL_DOWN"
    const val CH_UP = "CH_UP"
    const val CH_DOWN = "CH_DOWN"
    const val DIGIT_0 = "DIGIT_0"
    const val DIGIT_1 = "DIGIT_1"
    const val DIGIT_2 = "DIGIT_2"
    const val DIGIT_3 = "DIGIT_3"
    const val DIGIT_4 = "DIGIT_4"
    const val DIGIT_5 = "DIGIT_5"
    const val DIGIT_6 = "DIGIT_6"
    const val DIGIT_7 = "DIGIT_7"
    const val DIGIT_8 = "DIGIT_8"
    const val DIGIT_9 = "DIGIT_9"
    const val SOURCE = "SOURCE"
    const val HDMI1 = "HDMI1"
    const val HDMI2 = "HDMI2"
    const val HDMI3 = "HDMI3"
    const val HDMI4 = "HDMI4"
    const val MENU = "MENU"
    const val HOME = "HOME"
    const val OK = "OK"
    const val UP = "UP"
    const val DOWN = "DOWN"
    const val LEFT = "LEFT"
    const val RIGHT = "RIGHT"
    const val BACK = "BACK"
    const val EXIT = "EXIT"
    const val INFO = "INFO"
    const val LIST = "LIST"
    const val PRE_CH = "PRE_CH"
    const val RED = "RED"
    const val GREEN = "GREEN"
    const val YELLOW = "YELLOW"
    const val BLUE = "BLUE"

    val HDMI: List<String> = listOf(HDMI1, HDMI2, HDMI3, HDMI4)

    /** Tasten, die beim Halten wiederholt gesendet werden. */
    val REPEATING: Set<String> = setOf(VOL_UP, VOL_DOWN, CH_UP, CH_DOWN, UP, DOWN, LEFT, RIGHT)

    fun isExtra(key: String) = key.startsWith("X:")

    fun extraLabel(key: String) = key.removePrefix("X:")
}
