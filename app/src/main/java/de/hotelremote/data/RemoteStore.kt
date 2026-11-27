package de.hotelremote.data

import de.hotelremote.ir.IrSignal
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Gespeicherter Raum: Fernseher ([keys], eigene Kopie aller Signale, unabhängig von der Code-Datenbank) und/oder
 * Klimaanlage ([ac]). Ein reiner Klima-Raum hat keine Tasten; [brand] und [codeLabel] sind dann leer.
 */
@Serializable
data class SavedRemote(
    val id: String,
    val name: String? = null,
    val brand: String = "",
    val codeLabel: String = "",
    val keys: Map<String, IrSignal> = emptyMap(),
    val lastUsed: Long,
    val automations: List<Automation> = emptyList(),
    val ac: SavedAc? = null,
    /** Zuletzt gezeigte Wischseite (Raum mit Fernseher und Klimaanlage); null = TV. */
    val lastPage: RoomPage? = null,
) {
    val hasTv get() = keys.isNotEmpty()

    val title get() = name?.takeIf { it.isNotBlank() } ?: if (!hasTv && ac != null) ac.title else "$brand · $codeLabel"
}

/** Ablage der gespeicherten Fernbedienungen; Schreibfehler als [java.io.IOException]. */
interface RemoteStorage {
    fun all(): List<SavedRemote>
    fun add(r: SavedRemote)
    fun rename(id: String, name: String?)
    fun delete(id: String)
    fun markUsed(id: String, now: Long)
    fun updateAutomations(id: String, automations: List<Automation>)

    /** Setzt den Fernseher des Raums; leere [keys] (und leere Texte) entfernen ihn. */
    fun setTv(id: String, brand: String, codeLabel: String, keys: Map<String, IrSignal>)

    /** Setzt die Klimaanlage des Raums; null entfernt sie. */
    fun setAc(id: String, ac: SavedAc?)

    /** Neuer Zustand der Klimaanlage des Raums (ohne Klimaanlage: keine Änderung). */
    fun updateAcState(id: String, state: AcState)

    /** Merkt die zuletzt gezeigte Wischseite des Raums. */
    fun setLastPage(id: String, page: RoomPage)

    /** Einstellungen der Klima-Automatik des Raums (ohne Klimaanlage: keine Änderung). */
    fun setAcAuto(id: String, settings: AcAutoSettings)
}

/** JSON-Datei mit gespeicherten Fernbedienungen; Schreiben atomar über `<file>.tmp`. */
class RemoteStore(private val file: File) : RemoteStorage {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedRemote.serializer())

    @Synchronized
    override fun all(): List<SavedRemote> = load().sortedByDescending { it.lastUsed }

    @Synchronized
    override fun add(r: SavedRemote) {
        save(load().filterNot { it.id == r.id } + r)
    }

    @Synchronized
    override fun rename(id: String, name: String?) = update(id) { it.copy(name = name) }

    @Synchronized
    override fun delete(id: String) {
        val list = load()
        if (list.any { it.id == id }) save(list.filterNot { it.id == id })
    }

    @Synchronized
    override fun markUsed(id: String, now: Long) = update(id) { it.copy(lastUsed = now) }

    @Synchronized
    override fun updateAutomations(id: String, automations: List<Automation>) = update(id) { it.copy(automations = automations) }

    @Synchronized
    override fun setTv(id: String, brand: String, codeLabel: String, keys: Map<String, IrSignal>) =
        update(id) { it.copy(brand = brand, codeLabel = codeLabel, keys = keys) }

    @Synchronized
    override fun setAc(id: String, ac: SavedAc?) = update(id) { it.copy(ac = ac) }

    @Synchronized
    override fun updateAcState(id: String, state: AcState) = update(id) { r -> r.copy(ac = r.ac?.copy(state = state)) }

    @Synchronized
    override fun setLastPage(id: String, page: RoomPage) = update(id) { it.copy(lastPage = page) }

    @Synchronized
    override fun setAcAuto(id: String, settings: AcAutoSettings) = update(id) { r -> r.copy(ac = r.ac?.copy(auto = settings)) }

    @Synchronized
    fun lastUsed(): SavedRemote? = all().firstOrNull()

    /** Ändert den Eintrag [id]; ist er unbekannt oder bleibt er gleich, wird nicht geschrieben. */
    private fun update(id: String, change: (SavedRemote) -> SavedRemote) {
        val list = load()
        val old = list.firstOrNull { it.id == id } ?: return
        val new = change(old)
        if (new == old) return
        save(list.map { if (it.id == id) new else it })
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
