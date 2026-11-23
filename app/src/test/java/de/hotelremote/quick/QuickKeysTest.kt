package de.hotelremote.quick

import de.hotelremote.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Welche Taste eine Schnellsteuerungs-Aktion sendet. */
class QuickKeysTest {
    @Test fun powerPrefersToggleThenShortPressLogic() {
        assertEquals(Keys.POWER, quickKey(QuickAction.POWER, setOf(Keys.POWER, Keys.POWER_ON, Keys.POWER_OFF), null))
        assertEquals(Keys.POWER_ON, quickKey(QuickAction.POWER, setOf(Keys.POWER_ON, Keys.POWER_OFF), null))
        assertEquals(Keys.POWER_OFF, quickKey(QuickAction.POWER, setOf(Keys.POWER_OFF), null))
        assertNull(quickKey(QuickAction.POWER, setOf(Keys.VOL_UP), null))
    }

    @Test fun volumeKeys() {
        val keys = setOf(Keys.VOL_UP, Keys.VOL_DOWN)
        assertEquals(Keys.VOL_DOWN, quickKey(QuickAction.VOL_DOWN, keys, null))
        assertEquals(Keys.VOL_UP, quickKey(QuickAction.VOL_UP, keys, null))
        assertNull(quickKey(QuickAction.VOL_UP, setOf(Keys.VOL_DOWN), null))
    }

    @Test fun inputUsesChosenKeyThenHdmi1ThenSource() {
        val all = setOf(Keys.HDMI1, Keys.HDMI2, Keys.HDMI3, Keys.SOURCE)
        assertEquals(Keys.HDMI3, quickKey(QuickAction.INPUT, all, Keys.HDMI3))
        assertEquals(Keys.SOURCE, quickKey(QuickAction.INPUT, all, Keys.SOURCE))
        assertEquals(Keys.HDMI1, quickKey(QuickAction.INPUT, all, null))
        // Gewählte Taste fehlt dieser Fernbedienung: Standard.
        assertEquals(Keys.HDMI1, quickKey(QuickAction.INPUT, setOf(Keys.HDMI1, Keys.SOURCE), Keys.HDMI4))
        assertEquals(Keys.SOURCE, quickKey(QuickAction.INPUT, setOf(Keys.SOURCE), Keys.HDMI2))
        assertNull(quickKey(QuickAction.INPUT, setOf(Keys.POWER), null))
    }

    @Test fun inputKeyIgnoresNonInputChoice() {
        assertEquals(Keys.SOURCE, quickKey(QuickAction.INPUT, setOf(Keys.SOURCE, Keys.POWER), Keys.POWER))
    }

    @Test fun inputChoicesOnlyPresentKeysInOrder() {
        assertEquals(
            listOf(Keys.HDMI1, Keys.HDMI3, Keys.SOURCE),
            inputChoices(setOf(Keys.SOURCE, Keys.HDMI3, Keys.POWER, Keys.HDMI1)),
        )
        assertEquals(emptyList<String>(), inputChoices(setOf(Keys.POWER)))
    }
}
