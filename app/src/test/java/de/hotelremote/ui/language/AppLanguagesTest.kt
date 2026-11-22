package de.hotelremote.ui.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguagesTest {
    private fun tagOf(tag: String) = AppLanguages.match(tag)?.tag

    @Test fun elevenLanguagesGermanFirst() {
        assertEquals(
            listOf("de", "en", "zh-CN", "hi", "es", "fr", "ar", "bn", "pt", "ru", "id"),
            AppLanguages.all.map { it.tag },
        )
    }

    @Test fun matchesByLanguageIgnoringRegion() {
        assertEquals("de", tagOf("de-AT"))
        assertEquals("en", tagOf("en-US"))
        assertEquals("es", tagOf("es-419"))
        assertEquals("fr", tagOf("fr-CA"))
        assertEquals("ar", tagOf("ar-EG"))
        assertEquals("ru", tagOf("ru"))
        assertEquals("hi", tagOf("hi-IN"))
        assertEquals("bn", tagOf("bn-BD"))
    }

    @Test fun brazilianAndEuropeanPortugueseMapToPt() {
        assertEquals("pt", tagOf("pt-BR"))
        assertEquals("pt", tagOf("pt-PT"))
    }

    @Test fun simplifiedChineseOnly() {
        assertEquals("zh-CN", tagOf("zh-CN"))
        assertEquals("zh-CN", tagOf("zh-Hans"))
        assertEquals("zh-CN", tagOf("zh-Hans-CN"))
        assertEquals("zh-CN", tagOf("zh"))
        assertEquals("zh-CN", tagOf("zh_SG"))
        assertNull(tagOf("zh-TW"))
        assertNull(tagOf("zh-Hant-HK"))
    }

    @Test fun indonesianAsInOrId() {
        assertEquals("id", tagOf("id"))
        assertEquals("id", tagOf("id-ID"))
        assertEquals("id", tagOf("in"))
        assertEquals("id", tagOf("in-ID"))
    }

    @Test fun unsupportedOrEmpty() {
        assertNull(tagOf("ja-JP"))
        assertNull(AppLanguages.match(""))
        assertNull(AppLanguages.match(null))
    }

    @Test fun firstStartPreselectsSystemLanguageElseEnglish() {
        assertEquals("fr", AppLanguages.preselect(emptyList(), listOf("fr-FR"), firstStart = true).tag)
        assertEquals("pt", AppLanguages.preselect(emptyList(), listOf("pt-BR"), firstStart = true).tag)
        assertEquals("zh-CN", AppLanguages.preselect(emptyList(), listOf("zh-Hans-CN"), firstStart = true).tag)
        assertEquals("en", AppLanguages.preselect(emptyList(), listOf("ja-JP"), firstStart = true).tag)
        assertEquals("en", AppLanguages.preselect(emptyList(), emptyList(), firstStart = true).tag)
        // Erste unterstützte Systemsprache aus der Liste.
        assertEquals("de", AppLanguages.preselect(emptyList(), listOf("ja-JP", "de-DE"), firstStart = true).tag)
    }

    @Test fun laterPreselectsCurrentAppLanguage() {
        assertEquals("ru", AppLanguages.preselect(listOf("ru"), listOf("de-DE"), firstStart = false).tag)
        assertEquals("id", AppLanguages.preselect(listOf("in"), listOf("en-US"), firstStart = false).tag)
        // Keine App-Sprache gesetzt: Systemsprache; passt keine, zeigt die App Deutsch (values/).
        assertEquals("en", AppLanguages.preselect(emptyList(), listOf("en-GB"), firstStart = false).tag)
        assertEquals("de", AppLanguages.preselect(emptyList(), listOf("ja-JP"), firstStart = false).tag)
    }
}
