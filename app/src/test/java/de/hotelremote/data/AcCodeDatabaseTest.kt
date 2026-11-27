package de.hotelremote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.InputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fixture: tools/tests/fixtures/smartir, gebaut von tools/build_ac_codes.py (siehe test_smartir.py). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AcCodeDatabaseTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val source: () -> InputStream =
        { javaClass.classLoader!!.getResourceAsStream("ac-fixture.db")!! }
    private lateinit var db: AcCodeDatabase

    @Before fun setUp() {
        db = AcCodeDatabase.open(ctx, stamp = 1, source = source)
    }

    @After fun tearDown() = db.close()

    private fun brandId(name: String) = db.brands().single { it.name == name }.id

    @Test fun copiesAssetUnderOwnNameNextToTvCodes() {
        assertTrue(File(ctx.noBackupFilesDir, "ac_codes-1.db").exists())
        CodeDatabase.open(ctx, stamp = 1, source = { javaClass.classLoader!!.getResourceAsStream("codes-fixture.db")!! })
            .close()
        assertTrue("TV-Kopie räumt die Klima-Kopie nicht weg", File(ctx.noBackupFilesDir, "ac_codes-1.db").exists())
        db.close()
        db = AcCodeDatabase.open(ctx, stamp = 2, source = source)
        assertFalse(File(ctx.noBackupFilesDir, "ac_codes-1.db").exists())
        assertTrue(File(ctx.noBackupFilesDir, "codes-1.db").exists())
    }

    @Test fun brandsSortedWithDeviceCounts() {
        assertEquals(
            listOf(AcBrand(1, "Acme", 2), AcBrand(2, "Beta", 1), AcBrand(3, "General Electric", 1)),
            db.brands(),
        )
    }

    @Test fun devicesWithMoreModelsFirst() {
        val acme = db.devices(brandId("Acme"))
        assertEquals(listOf(listOf("A1", "A2"), listOf("A3")), acme.map { it.models })
        assertEquals("1000.json,1001.json", acme[0].source)
    }

    @Test fun deviceColumns() {
        val ge = db.devices(brandId("General Electric")).single()
        assertEquals(ge.id, db.device(ge.id)!!.id)
        assertEquals("General Electric", ge.brandName)
        assertEquals(listOf("cool"), ge.modes)
        assertEquals(listOf("auto"), ge.fanModes)
        assertEquals(listOf("off", "swing"), ge.swingModes)
        assertEquals(22.0, ge.minTemp, 0.0)
        assertEquals(22.5, ge.maxTemp, 0.0)
        assertEquals(0.5, ge.precision, 0.0)
        val acme = db.devices(brandId("Acme")).first()
        assertEquals(emptyList<String>(), acme.swingModes)
        assertEquals(listOf("cool", "heat"), acme.modes)
        assertNull(db.device(999))
    }

    @Test fun shippedAssetDecodesEveryDevice() {
        AcCodeDatabase.open(ctx, stamp = 3).use { real ->
            assertEquals("3dca407e3c475b57c02c08b97ae521a4ef0bbf43", real.meta()["smartir_commit"])
            val devices = real.brands().flatMap { real.devices(it.id) }
            assertEquals(real.meta().getValue("devices").toInt(), devices.size)
            for (d in devices) {
                val codes = d.codes
                assertTrue(d.source, codes.off != null && "cool" in codes.modes())
                assertTrue(d.source, codes.paths.all { p -> codes.pulses(p)!!.let { it.size % 2 == 1 && it.sum() <= 1_500_000 } })
            }
        }
    }

    @Test fun deviceIdsInTestOrderWithoutBlobs() {
        val acme = brandId("Acme")
        assertEquals(db.devices(acme).map { it.id }, db.deviceIds(acme))
        val all = db.brands().flatMap { b -> db.devices(b.id).map { it.id } }
        assertEquals(all, db.deviceIds(null))
    }

    @Test fun shippedAssetPutsCommonBrandsFirstForUnknownBrand() {
        AcCodeDatabase.open(ctx, stamp = 4).use { real ->
            val brands = real.brands()
            assertEquals(0, brands.single { it.name == "Daikin" }.commonRank)
            assertEquals(1, brands.single { it.name == "Mitsubishi Electric" }.commonRank)
            assertNull(brands.single { it.name == "Akai" }.commonRank)
            val ids = real.deviceIds(null)
            assertEquals(real.meta().getValue("devices").toInt(), ids.size)
            assertEquals(ids.size, ids.toSet().size)
            val daikin = real.deviceIds(brands.single { it.name == "Daikin" }.id)
            assertEquals(daikin, ids.take(daikin.size))
            assertEquals("Mitsubishi Electric", real.device(ids[daikin.size])!!.brandName)
        }
    }

    @Test fun metaNamesSourceAndLicense() {
        val meta = db.meta()
        assertTrue(meta.getValue("source").startsWith("SmartIR"))
        assertTrue(meta.getValue("license").startsWith("MIT"))
        assertEquals("4", meta["devices"])
    }
}
