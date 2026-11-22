package de.hotelremote.ir

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidIrTransmitterTest {
    @Test(expected = UnsupportedOperationException::class)
    fun transmitWithoutEmitterThrows() {
        val tx = AndroidIrTransmitter(ApplicationProvider.getApplicationContext())
        assertFalse(tx.available)
        tx.transmit(38000, intArrayOf(9000, 4500, 560))
    }
}
