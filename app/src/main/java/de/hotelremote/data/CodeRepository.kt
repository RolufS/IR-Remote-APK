package de.hotelremote.data

import android.database.sqlite.SQLiteDatabase
import de.hotelremote.ir.IrSignal
import de.hotelremote.wizard.WizardData
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Brand(val id: Long, val name: String, val hotelRank: Int?, val codeSetCount: Int)

data class ScanEntry(
    val position: Int,
    val codeSetId: Long,
    val brandId: Long,
    val brandName: String,
    val signal: IrSignal,
)

class CodeRepository(private val db: SQLiteDatabase) : WizardData {

    fun brands(): List<Brand> {
        val all = db.rawQuery(
            """SELECT b.id, b.name, b.hotel_rank, COUNT(*) FROM brand b
               JOIN code_set_brand c ON c.brand_id = b.id GROUP BY b.id""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Brand(c.getLong(0), c.getString(1), if (c.isNull(2)) null else c.getInt(2), c.getInt(3)))
                }
            }
        }
        val (hotel, rest) = all.partition { it.hotelRank != null }
        return hotel.sortedBy { it.hotelRank } + rest.sortedBy { it.name.lowercase() }
    }

    fun meta(): Map<String, String> =
        db.rawQuery("SELECT k, v FROM meta", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    override fun codeSetsForBrand(brandId: Long): List<Long> =
        db.rawQuery(
            "SELECT code_set_id FROM code_set_brand WHERE brand_id = ? ORDER BY ord",
            arrayOf(brandId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    override fun keys(codeSetId: Long): Map<String, IrSignal> =
        db.rawQuery(SIGNAL_SELECT_PREFIX + "k.key FROM code_set_key k JOIN signal s ON s.id = k.signal_id WHERE k.code_set_id = ?",
            arrayOf(codeSetId.toString()),
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getString(5), signalAt(c)) } }

    override fun inputVariants(codeSetId: Long, brandId: Long): List<Map<String, IrSignal>> {
        val variantIds = db.rawQuery(
            "SELECT variant_id FROM input_variant_order WHERE code_set_id = ? AND brand_id = ? ORDER BY ord",
            arrayOf(codeSetId.toString(), brandId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        return variantIds.map { id ->
            db.rawQuery(
                SIGNAL_SELECT_PREFIX + "k.key FROM input_variant_key k JOIN signal s ON s.id = k.signal_id WHERE k.variant_id = ?",
                arrayOf(id.toString()),
            ).use { c -> buildMap { while (c.moveToNext()) put(c.getString(5), signalAt(c)) } }
        }
    }

    override fun scanEntries(): List<ScanEntry> =
        db.rawQuery(
            SIGNAL_SELECT_PREFIX + """sc.position, sc.code_set_id, sc.brand_id, b.name FROM scan sc
               JOIN signal s ON s.id = sc.signal_id JOIN brand b ON b.id = sc.brand_id
               ORDER BY sc.position""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(ScanEntry(c.getInt(5), c.getLong(6), c.getLong(7), c.getString(8), signalAt(c)))
            }
        }

    override fun brandName(brandId: Long): String =
        db.rawQuery("SELECT name FROM brand WHERE id = ?", arrayOf(brandId.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else error("Unbekannte Marke $brandId")
        }

    private fun signalAt(c: android.database.Cursor) = IrSignal(
        freq = c.getInt(0),
        frame = decode(c.getBlob(1)),
        repeat = if (c.isNull(2)) null else decode(c.getBlob(2)),
        frameAlt = if (c.isNull(3)) null else decode(c.getBlob(3)),
        periodMs = c.getInt(4),
    )

    private fun decode(blob: ByteArray): List<Int> {
        val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return List(blob.size / 4) { buf.getInt() }
    }

    private companion object {
        const val SIGNAL_SELECT_PREFIX = "SELECT s.freq, s.frame, s.repeat, s.frame_alt, s.period_ms, "
    }
}
