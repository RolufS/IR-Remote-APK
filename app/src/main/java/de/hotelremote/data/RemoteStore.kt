package de.hotelremote.data

import de.hotelremote.ir.IrSignal
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Gespeicherte Fernbedienung mit eigener Kopie aller Signale (unabhängig von der Code-Datenbank). */
@Serializable
data class SavedRemote(
    val id: String,
    val name: String? = null,
    val brand: String,
    val codeLabel: String,
    val keys: Map<String, IrSignal>,
    val lastUsed: Long,
) {
    val title get() = name?.takeIf { it.isNotBlank() } ?: "$brand · $codeLabel"
}

/** JSON-Datei mit gespeicherten Fernbedienungen; Schreiben atomar über `<file>.tmp`. */
class RemoteStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedRemote.serializer())

    @Synchronized
    fun all(): List<SavedRemote> = load().sortedByDescending { it.lastUsed }

    @Synchronized
    fun add(r: SavedRemote) {
        save(load().filterNot { it.id == r.id } + r)
    }

    @Synchronized
    fun rename(id: String, name: String?) = update(id) { it.copy(name = name) }

    @Synchronized
    fun delete(id: String) {
        val list = load()
        if (list.any { it.id == id }) save(list.filterNot { it.id == id })
    }

    @Synchronized
    fun markUsed(id: String, now: Long) = update(id) { it.copy(lastUsed = now) }

    @Synchronized
    fun lastUsed(): SavedRemote? = all().firstOrNull()

    private fun update(id: String, change: (SavedRemote) -> SavedRemote) {
        val list = load()
        if (list.none { it.id == id }) return
        save(list.map { if (it.id == id) change(it) else it })
    }

    private fun load(): List<SavedRemote> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            backupCorrupt()
            emptyList()
        }
    }

    private fun backupCorrupt() {
        try {
            moveReplacing(file, File(file.path + ".bak"))
        } catch (e: Exception) {
            file.delete()
        }
    }

    private fun save(list: List<SavedRemote>) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, list), Charsets.UTF_8)
        moveReplacing(tmp, file)
    }

    /** File.renameTo ersetzt auf Android (POSIX) atomar; auf Windows/JVM schlägt es bei vorhandenem Ziel fehl. */
    private fun moveReplacing(from: File, to: File) {
        if (from.renameTo(to)) return
        to.delete()
        if (!from.renameTo(to)) throw java.io.IOException("Konnte ${from.name} nicht nach ${to.name} verschieben")
    }
}
