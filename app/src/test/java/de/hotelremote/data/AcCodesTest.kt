package de.hotelremote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pulse wie in tools/tests/test_smartir.py (ACME_OFF, ACME_COOL_AUTO_16, BETA_OFF). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AcCodesTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AcCodeDatabase
    private lateinit var acme: AcCodes
    private lateinit var ge: AcCodes
    private lateinit var beta: AcCodes

    @Before fun setUp() {
        db = AcCodeDatabase.open(ctx, stamp = 1, source = {
            javaClass.classLoader!!.getResourceAsStream("ac-fixture.db")!!
        })
        fun first(brand: String) = db.devices(db.brands().single { it.name == brand }.id).first().codes
        acme = first("Acme")
        ge = first("General Electric")
        beta = first("Beta")
    }

    @After fun tearDown() = db.close()

    @Test fun sharedFixturePulses() {
        assertArrayEquals(intArrayOf(8997, 4499, 558, 1675, 558), acme.pulses("off"))
        assertArrayEquals(
            intArrayOf(8997, 4499, 558, 1675, 558, 558, 558, 558, 558, 558, 558),
            acme.pulses("cool/auto/16"),
        )
        assertArrayEquals(intArrayOf(4000, 1500, 600), beta.pulses("off"))
        val repeated = acme.pulses("heat/low/17")!!
        assertEquals(23, repeated.size)
        assertEquals(100_000, repeated[11])
    }

    @Test fun signalForExactStateOrNull() {
        val s = acme.signalFor(true, "cool", "auto", null, 16.0)!!
        assertEquals(38_000, s.freq)
        assertEquals(acme.pulses("cool/auto/16")!!.toList(), s.frame)
        assertNull(s.repeat)
        assertTrue(s.periodMs > s.frame.sum() / 1000)
        assertNull(acme.signalFor(true, "cool", "auto", null, 18.0))
        assertNull(acme.signalFor(true, "dry", "auto", null, 16.0))
        assertNull("Swing angegeben, Gerät hat keinen", acme.signalFor(true, "cool", "auto", "off", 16.0))
        assertEquals(acme.off, acme.signalFor(false, "egal", "egal", null, 0.0))
        assertEquals(listOf(8997, 4499, 558, 1675, 558), acme.off!!.frame)
    }

    @Test fun helpersWithoutSwing() {
        assertFalse(acme.hasSwing)
        assertEquals(listOf("cool", "heat"), acme.modes())
        assertEquals(listOf("auto", "low"), acme.fans("cool"))
        assertEquals(emptyList<String>(), acme.swings("cool", "auto"))
        assertEquals(listOf(16.0, 17.0), acme.temps("cool", "low"))
        assertEquals(emptyList<Double>(), acme.temps("cool", "low", "off"))
        assertEquals(listOf("off", "cool/auto/20"), db.devices(1).last().codes.paths.toList())
    }

    @Test fun helpersWithSwingAndHalfDegrees() {
        assertTrue(ge.hasSwing)
        assertEquals(listOf("off", "swing"), ge.swings("cool", "auto"))
        assertEquals(listOf(22.0, 22.5), ge.temps("cool", "auto", "swing"))
        assertEquals(emptyList<Double>(), ge.temps("cool", "auto"))
        assertEquals(ge.pulses("cool/auto/swing/22.5")!!.toList(), ge.signalFor(true, "cool", "auto", "swing", 22.5)!!.frame)
        assertNull(ge.signalFor(true, "cool", "auto", null, 22.0))
    }

    @Test fun pathAndTempKeyMatchPython() {
        assertEquals("cool/auto/22", AcCodes.path("cool", "auto", null, 22.0))
        assertEquals("cool/auto/off/22.5", AcCodes.path("cool", "auto", "off", 22.5))
        assertEquals("16.6", AcCodes.tempKey(16.6))
    }

    /** Gleiche Bytes wie test_blob_layout in tools/tests/test_smartir.py. */
    @Test fun decodesHandBuiltBlob() {
        val raw = byteArrayOf(1, 2, 0) + le32(500) + le32(1000) + byteArrayOf(1, 0, 3) +
            "off".toByteArray() + byteArrayOf(3, 0, 0, 1, 0)
        val codes = AcCodes.decode(deflate(raw))
        assertEquals(setOf("off"), codes.paths)
        assertArrayEquals(intArrayOf(500, 1000, 500), codes.pulses("off"))
        assertEquals(emptyList<String>(), codes.modes())
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownVersion() {
        AcCodes.decode(deflate(byteArrayOf(2, 0, 0, 0, 0)))
    }

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }
}
