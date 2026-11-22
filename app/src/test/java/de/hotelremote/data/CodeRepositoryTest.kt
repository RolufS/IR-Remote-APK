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
}
