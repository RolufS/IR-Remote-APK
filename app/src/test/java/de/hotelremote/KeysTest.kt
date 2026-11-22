package de.hotelremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeysTest {
    @Test
    fun repeatingContainsVolumeChannelAndDpad() {
        assertEquals(
            setOf("VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN", "UP", "DOWN", "LEFT", "RIGHT"),
            Keys.REPEATING,
        )
    }

    @Test
    fun hdmiListsAllFourInputs() {
        assertEquals(listOf("HDMI1", "HDMI2", "HDMI3", "HDMI4"), Keys.HDMI)
    }

    @Test
    fun extraKeysAreRecognisedAndLabelled() {
        assertTrue(Keys.isExtra("X:Netflix"))
        assertFalse(Keys.isExtra(Keys.POWER))
        assertEquals("Netflix", Keys.extraLabel("X:Netflix"))
    }
}
