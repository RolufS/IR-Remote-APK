package de.hotelremote.data

import de.hotelremote.ir.IrSignal
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * Alle Kommandos einer Klimaanlage, dekodiert aus dem Blob `ac_device.commands`.
 *
 * Blob-Format (erzeugt von tools/irdata/acblob.py), zlib-komprimiert, darin little-endian:
 * ```
 * u8   Version (1)
 * u16  Anzahl Symbole N, danach N x u32 Dauer in µs
 * u16  Anzahl Kommandos M, danach je Kommando:
 *      u8 Pfadlänge, Pfad (UTF-8), u16 Anzahl Pulse P, P x u8 Symbolindex
 * ```
 * Pfade: `off`, `<mode>/<fan>/<temp>` oder `<mode>/<fan>/<swing>/<temp>`; die Temperatur ist
 * ganzzahlig ohne Nachkommastelle ("22"), sonst wie Double.toString ("22.5").
 * Pulse in µs, abwechselnd an/aus, beginnend und endend mit "an"; Träger 38 kHz.
 *
 * Es gibt keine stille Ersatzwahl: [signalFor] liefert null, wenn die Kombination fehlt.
 */
class AcCodes private constructor(private val commands: Map<String, IntArray>) {

    /** Pfade in Blob-Reihenfolge (Reihenfolge der SmartIR-Datei). */
    val paths: Set<String> get() = commands.keys

    private val states: List<List<String>> =
        commands.keys.filter { it != OFF }.map { it.split('/') }

    /** true, wenn die Kommandos eine Swing-Ebene haben (mode/fan/swing/temp). */
    val hasSwing: Boolean = states.firstOrNull()?.size == 4

    fun pulses(path: String): IntArray? = commands[path]?.copyOf()

    val off: IrSignal? get() = signal(OFF)

    fun modes(): List<String> = states.map { it[0] }.distinct()

    fun fans(mode: String): List<String> =
        states.filter { it[0] == mode }.map { it[1] }.distinct()

    /** Swing-Stellungen für Modus und Lüfter; leer bei Geräten ohne Swing-Ebene. */
    fun swings(mode: String, fan: String): List<String> =
        if (!hasSwing) emptyList()
        else states.filter { it[0] == mode && it[1] == fan }.map { it[2] }.distinct()

    /** Vorhandene Temperaturen, aufsteigend. Bei Geräten mit Swing-Ebene ist [swing] nötig. */
    fun temps(mode: String, fan: String, swing: String? = null): List<Double> =
        states.filter {
            it[0] == mode && it[1] == fan && (if (hasSwing) it[2] == swing else swing == null)
        }.map { it.last().toDouble() }.distinct().sorted()

    /** Exakt das Signal für diesen Zustand, sonst null. Ausgeschaltet heißt immer `off`. */
    fun signalFor(power: Boolean, mode: String, fan: String, swing: String?, temp: Double): IrSignal? {
        if (!power) return off
        if (hasSwing != (swing != null)) return null
        return signal(path(mode, fan, swing, temp))
    }

    private fun signal(path: String): IrSignal? {
        val p = commands[path] ?: return null
        return IrSignal(freq = FREQUENCY, frame = p.toList(), periodMs = (p.sum() + 999) / 1000 + GAP_MS)
    }

    companion object {
        const val FREQUENCY = 38_000
        const val OFF = "off"
        private const val VERSION = 1
        /** Abstand bis zur Wiederholung beim Halten (bei Klimaanlagen kaum genutzt). */
        private const val GAP_MS = 100

        fun path(mode: String, fan: String, swing: String?, temp: Double): String =
            listOfNotNull(mode, fan, swing, tempKey(temp)).joinToString("/")

        fun tempKey(temp: Double): String =
            if (temp == Math.floor(temp) && !temp.isInfinite()) temp.toLong().toString() else temp.toString()

        fun decode(blob: ByteArray): AcCodes {
            val buf = ByteBuffer.wrap(inflate(blob)).order(ByteOrder.LITTLE_ENDIAN)
            val version = buf.get().toInt() and 0xFF
            require(version == VERSION) { "Klima-Blob Version $version" }
            val symbols = IntArray(buf.short.toInt() and 0xFFFF) { buf.int }
            val count = buf.short.toInt() and 0xFFFF
            val commands = LinkedHashMap<String, IntArray>(count * 2)
            repeat(count) {
                val pathBytes = ByteArray(buf.get().toInt() and 0xFF)
                buf.get(pathBytes)
                val path = pathBytes.toString(Charsets.UTF_8)
                val pulses = IntArray(buf.short.toInt() and 0xFFFF) { symbols[buf.get().toInt() and 0xFF] }
                commands[path] = pulses
            }
            require(!buf.hasRemaining()) { "Klima-Blob: Restbytes" }
            return AcCodes(commands)
        }

        private fun inflate(blob: ByteArray): ByteArray {
            val inflater = Inflater()
            try {
                inflater.setInput(blob)
                val out = ByteArrayOutputStream(blob.size * 4)
                val chunk = ByteArray(8192)
                while (!inflater.finished()) {
                    val n = inflater.inflate(chunk)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        error("Klima-Blob unvollständig")
                    }
                    out.write(chunk, 0, n)
                }
                return out.toByteArray()
            } finally {
                inflater.end()
            }
        }
    }
}
