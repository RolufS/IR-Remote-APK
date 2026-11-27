package de.hotelremote.data

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Baut ein Klima-Blob (Format v1, siehe [AcCodes]) aus Pfaden. Jeder Pfad bekommt die Pulse
 * `500, 1000 + Index, 500`, damit Tests am Signal erkennen, welcher Pfad gesendet wurde.
 */
internal fun acBlob(paths: List<String>): ByteArray {
    require(paths.size < 255)
    val out = ByteArrayOutputStream()
    fun u16(v: Int) {
        out.write(v and 0xFF)
        out.write(v shr 8 and 0xFF)
    }
    fun u32(v: Int) {
        u16(v and 0xFFFF)
        u16(v ushr 16)
    }
    out.write(1)
    u16(paths.size + 1)
    u32(500)
    paths.indices.forEach { u32(1000 + it) }
    u16(paths.size)
    paths.forEachIndexed { i, path ->
        val bytes = path.toByteArray(Charsets.UTF_8)
        out.write(bytes.size)
        out.write(bytes)
        u16(3)
        out.write(0)
        out.write(i + 1)
        out.write(0)
    }
    return deflate(out.toByteArray())
}

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

/** Kühlen (Auto 20–24, Low 22–23), Heizen (Auto 18–20), Lüften mit SmartIR-Schreibweise "High" (nur 24). */
internal val PLAIN_PATHS = listOf("off") +
    (20..24).map { "cool/auto/$it" } + listOf("cool/low/22", "cool/low/23") +
    (18..20).map { "heat/auto/$it" } + listOf("fan_only/High/24")

/** Mit Swing-Ebene und halben Graden. */
internal val SWING_PATHS = listOf("off") +
    listOf("off", "swing").flatMap { sw -> listOf("22", "22.5", "23").map { "cool/auto/$sw/$it" } } +
    listOf("heat/auto/off/20")

internal fun testAc(
    paths: List<String> = PLAIN_PATHS,
    state: AcState = AcState(power = true, mode = "cool", temp = 22.0, fan = "auto"),
    maxTemp: Double = 30.0,
) = SavedAc(
    brand = "Acme",
    deviceLabel = "AC-1",
    minTemp = 16.0,
    maxTemp = maxTemp,
    precision = 1.0,
    modes = listOf("cool", "heat"),
    fanModes = listOf("auto", "low"),
    commands = SavedAc.encodeCommands(acBlob(paths)),
    state = state,
)

/** Pulse, die das Blob aus [paths] für [path] enthält. */
internal fun pulsesOf(paths: List<String>, path: String): List<Int> = listOf(500, 1000 + paths.indexOf(path), 500)
