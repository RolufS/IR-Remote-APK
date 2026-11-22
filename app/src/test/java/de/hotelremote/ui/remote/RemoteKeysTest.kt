package de.hotelremote.ui.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteKeysTest {
    @Test
    fun powerPrefersToggle() {
        assertEquals("POWER", powerAction(setOf("POWER", "POWER_OFF"), longPress = true))
        assertEquals("POWER", powerAction(setOf("POWER", "POWER_OFF"), longPress = false))
    }

    @Test
    fun powerOnOffSplit() {
        val keys = setOf("POWER_ON", "POWER_OFF")
        assertEquals("POWER_ON", powerAction(keys, longPress = false))
        assertEquals("POWER_OFF", powerAction(keys, longPress = true))
    }

    @Test
    fun powerOnlyOff() {
        assertEquals("POWER_OFF", powerAction(setOf("POWER_OFF"), longPress = false))
        assertEquals("POWER_OFF", powerAction(setOf("POWER_OFF"), longPress = true))
    }

    @Test
    fun powerOnlyOnFallsBackOnLongPress() {
        assertEquals("POWER_ON", powerAction(setOf("POWER_ON"), longPress = true))
    }

    @Test
    fun noPower() {
        assertNull(powerAction(emptySet(), longPress = false))
        assertNull(powerAction(emptySet(), longPress = true))
    }

    @Test
    fun inputRowOnlyWithHdmi1() {
        assertFalse(showInputRow(setOf("HDMI2")))
        assertTrue(showInputRow(setOf("HDMI1")))
    }
}
