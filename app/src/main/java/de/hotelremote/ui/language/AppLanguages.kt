package de.hotelremote.ui.language

import java.util.Locale

/** Eine Sprache der App: [tag] für `setApplicationLocales`, [nativeName] in der Sprache selbst. */
data class AppLanguage(val tag: String, val nativeName: String)

/** Die 11 Sprachen der App und die Zuordnung beliebiger Locale-Tags zu ihnen (ohne Android-Abhängigkeit). */
object AppLanguages {
    val GERMAN = AppLanguage("de", "Deutsch")
    val ENGLISH = AppLanguage("en", "English")

    val all: List<AppLanguage> = listOf(
        GERMAN,
        ENGLISH,
        AppLanguage("zh-CN", "中文（简体）"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("es", "Español"),
        AppLanguage("fr", "Français"),
        AppLanguage("ar", "العربية"),
        AppLanguage("bn", "বাংলা"),
        AppLanguage("pt", "Português"),
        AppLanguage("ru", "Русский"),
        // Android kennt Indonesisch als "in" (Ordner values-in), BCP 47 als "id".
        AppLanguage("id", "Bahasa Indonesia"),
    )

    /** Unterstützte Sprache zu einem Tag wie `pt-BR`, `zh-Hans-CN`, `in-ID`; null, wenn keine passt. */
    fun match(tag: String?): AppLanguage? {
        if (tag.isNullOrBlank()) return null
        val locale = Locale.forLanguageTag(tag.replace('_', '-'))
        val language = when (val l = locale.language) {
            "in" -> "id"
            else -> l
        }
        if (language == "zh") {
            // Nur vereinfachtes Chinesisch: Schrift Hans oder (ohne Schrift) Festland, Singapur, Malaysia.
            val simplified = locale.script == "Hans" ||
                (locale.script.isEmpty() && locale.country in setOf("", "CN", "SG", "MY"))
            return if (simplified) all.first { it.tag == "zh-CN" } else null
        }
        return all.firstOrNull { it.tag.substringBefore('-') == language }
    }

    /**
     * Vorauswahl auf dem Bildschirm „Sprache wählen“: die gewählte App-Sprache, sonst die erste
     * unterstützte Systemsprache. Passt keine, beim ersten Start Englisch, später Deutsch (das zeigt
     * die App dann, weil `values/` deutsch ist).
     */
    fun preselect(appTags: List<String>, systemTags: List<String>, firstStart: Boolean): AppLanguage =
        appTags.firstNotNullOfOrNull(::match)
            ?: systemTags.firstNotNullOfOrNull(::match)
            ?: if (firstStart) ENGLISH else GERMAN
}
