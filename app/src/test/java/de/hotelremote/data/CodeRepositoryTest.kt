package de.hotelremote.data

import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CodeRepositoryTest {
    private lateinit var db: SQLiteDatabase
    private lateinit var repo: CodeRepository
    private lateinit var file: File

    @Before fun setUp() {
        file = File.createTempFile("fixture", ".db")
        javaClass.classLoader!!.getResourceAsStream("codes-fixture.db")!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        repo = CodeRepository(db)
    }

    @After fun tearDown() {
        db.close()
        file.delete()
    }

    private fun brandId(name: String) = repo.brands().single { it.name == name }.id

    @Test fun brandsHotelFirstThenAlphabetical() {
        assertEquals(
            listOf(
                "Samsung", "Philips", "Hisense", "Telefunken", "Toshiba", "Vestel", "Acme", "Bush",
                "Digihome", "Finlux", "Hitachi", "JVC", "Luxor", "Polaroid", "Techwood",
            ),
            repo.brands().map { it.name },
        )
    }

    @Test fun samsungHasTwoCodeSetsMainFirst() {
        assertEquals(listOf(1L, 5L), repo.codeSetsForBrand(brandId("Samsung")))
    }

    @Test fun telefunkenOwnThenFamily() {
        assertEquals(listOf(6L, 2L), repo.codeSetsForBrand(brandId("Telefunken")))
    }

    @Test fun keysDecodeSamsungPower() {
        val keys = repo.keys(1L)
        val power = keys["POWER"]!!
        assertEquals(listOf(4512, 4512), power.frame.take(2))
        assertEquals(38000, power.freq)
        assertTrue(keys.keys.any { it == "X:Netflix" })
        assertFalse(keys.containsKey("HDMI1"))
    }

    @Test fun inputVariantsForMainSamsungSet() {
        assertEquals(
            setOf("HDMI1", "HDMI2", "HDMI3", "HDMI4"),
            repo.inputVariants(1L, brandId("Samsung")).single().keys,
        )
    }

    @Test fun scanEntriesOrderAndLabels() {
        assertEquals(
            listOf("Samsung", "Hisense", "Acme", "Philips", "Samsung", "Telefunken"),
            repo.scanEntries().map { it.brandName },
        )
    }

    @Test fun powerSiblingsEmptyWhenScanSignalUnique() {
        assertEquals(emptyList<Long>(), repo.powerSiblings(1L))
    }

    @Test fun powerSiblingsShareScanSignalOrderedBySupport() {
        db.close()
        db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        // Scan-Signal von Code-Satz 1 ist signal 5.
        db.execSQL("INSERT INTO code_set(id, support) VALUES (7, 5), (8, 9), (9, 50), (10, 9)")
        db.execSQL("INSERT INTO code_set_key VALUES (7, 'POWER', 5), (8, 'POWER_OFF', 5), (9, 'POWER_ON', 5), (10, 'POWER', 11)")
        db.execSQL("INSERT INTO code_set_brand VALUES (7, 2, 5, 1), (7, 3, 0, 0)")
        repo = CodeRepository(db)
        assertEquals(listOf(8L, 7L), repo.powerSiblings(1L))
        assertEquals(listOf(10L), repo.powerSiblings(2L))
        assertEquals(3L, repo.topBrand(7L))
        assertEquals(null, repo.topBrand(8L))
    }
}
