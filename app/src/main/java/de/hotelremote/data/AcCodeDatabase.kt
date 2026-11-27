package de.hotelremote.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.InputStream
import de.hotelremote.wizard.AcWizardData
import kotlinx.serialization.json.Json

/** [commonRank]: Platz in [AcCodeDatabase.COMMON_BRANDS] (0 = am häufigsten), null bei anderen Marken. */
data class AcBrand(val id: Long, val name: String, val deviceCount: Int, val commonRank: Int? = null)

/** Was der Klima-Assistent braucht: Marken und Geräte in Testreihenfolge. */
interface AcCatalog : AcWizardData {
    fun brands(): List<AcBrand>
}

/** Eine Klimaanlage (bzw. mehrere SmartIR-Dateien mit identischen Kommandos). */
class AcDevice(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val models: List<String>,
    /** SmartIR-Dateiname(n), kommagetrennt. */
    val source: String,
    val minTemp: Double,
    val maxTemp: Double,
    val precision: Double,
    val modes: List<String>,
    val fanModes: List<String>,
    /** Leer bei Geräten ohne Swing-Ebene. */
    val swingModes: List<String>,
    /** Rohes Blob (für eine eigene Kopie im gespeicherten Raum); dekodiert in [codes]. */
    val commands: ByteArray,
) {
    val codes: AcCodes by lazy { AcCodes.decode(commands) }
}

/** Lesezugriff auf das Asset `ac_codes.db` (erzeugt von tools/build_ac_codes.py). */
class AcCodeDatabase(private val db: SQLiteDatabase) : AcCatalog, Closeable {

    override fun brands(): List<AcBrand> =
        db.rawQuery(
            """SELECT b.id, b.name, COUNT(d.id) FROM ac_brand b
               JOIN ac_device d ON d.brand_id = b.id GROUP BY b.id ORDER BY b.sort_name, b.name""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(AcBrand(c.getLong(0), c.getString(1), c.getInt(2), commonRank(c.getString(1))))
            }
        }

    /**
     * Geräte-IDs in der Reihenfolge wie [devices] (ohne die Blobs zu laden). [brandId] null = alle Marken:
     * häufige Marken ([COMMON_BRANDS]) zuerst, danach alphabetisch.
     */
    override fun deviceIds(brandId: Long?): List<Long> {
        val where = if (brandId != null) "WHERE d.brand_id = ?" else ""
        data class Row(val id: Long, val brand: String, val sortName: String, val models: Int)
        val rows = db.rawQuery(
            """SELECT d.id, b.name, b.sort_name, d.models FROM ac_device d JOIN ac_brand b ON b.id = d.brand_id
               $where ORDER BY d.id""",
            brandId?.let { arrayOf(it.toString()) },
        ).use { c -> buildList { while (c.moveToNext()) add(Row(c.getLong(0), c.getString(1), c.getString(2), list(c.getString(3)).size)) } }
        return rows.sortedWith(
            compareBy<Row> { commonRank(it.brand) ?: Int.MAX_VALUE }.thenBy { it.sortName }.thenBy { it.brand }
                .thenByDescending { it.models },
        ).map { it.id }
    }

    /** Geräte einer Marke, Geräte mit mehr Modellen zuerst. */
    fun devices(brandId: Long): List<AcDevice> =
        query("WHERE d.brand_id = ? ORDER BY d.id", brandId.toString())
            .sortedByDescending { it.models.size }

    override fun device(id: Long): AcDevice? = query("WHERE d.id = ?", id.toString()).firstOrNull()

    fun meta(): Map<String, String> =
        db.rawQuery("SELECT key, value FROM meta", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    override fun close() = db.close()

    private fun query(where: String, arg: String): List<AcDevice> =
        db.rawQuery(
            """SELECT d.id, d.brand_id, b.name, d.models, d.source, d.min_temp, d.max_temp, d.precision,
                      d.modes, d.fan_modes, d.swing_modes, d.commands
               FROM ac_device d JOIN ac_brand b ON b.id = d.brand_id $where""",
            arrayOf(arg),
        ).use { c -> buildList { while (c.moveToNext()) add(deviceAt(c)) } }

    private fun deviceAt(c: Cursor) = AcDevice(
        id = c.getLong(0),
        brandId = c.getLong(1),
        brandName = c.getString(2),
        models = list(c.getString(3)),
        source = c.getString(4),
        minTemp = c.getDouble(5),
        maxTemp = c.getDouble(6),
        precision = c.getDouble(7),
        modes = list(c.getString(8)),
        fanModes = list(c.getString(9)),
        swingModes = list(c.getString(10)),
        commands = c.getBlob(11),
    )

    private fun list(json: String): List<String> = Json.decodeFromString(json)

    companion object {
        fun open(
            context: Context,
            stamp: Long = CodeDatabase.installStamp(context),
            source: () -> InputStream = { context.assets.open("$NAME.db") },
        ): AcCodeDatabase = AcCodeDatabase(CodeDatabase.open(context, stamp, NAME, source))

        private const val NAME = "ac_codes"

        /** Häufige Marken, oben in der Markenwahl und zuerst bei „Marke unbekannt“. */
        val COMMON_BRANDS = listOf(
            "Daikin", "Mitsubishi Electric", "LG", "Samsung", "Panasonic", "Gree", "Midea", "Fujitsu", "Hitachi",
            "Toshiba", "Haier", "Carrier", "Mitsubishi Heavy", "Hisense", "Sharp", "Electrolux",
        )

        fun commonRank(brand: String): Int? = COMMON_BRANDS.indexOf(brand).takeIf { it >= 0 }
    }
}
