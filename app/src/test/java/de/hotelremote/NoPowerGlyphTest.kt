package de.hotelremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Das Zeichen U+23FB fehlt in vielen Systemschriften (leeres Feld); Ein/Aus ist ein Vektor-Icon. */
class NoPowerGlyphTest {
    @Test fun noPowerGlyphInStringsOrKotlinSources() {
        val main = File("src/main")
        assertTrue("Arbeitsverzeichnis muss das Modul app/ sein", main.isDirectory)
        val files = main.walkTopDown().filter {
            it.isFile && (it.extension == "kt" || (it.extension == "xml" && it.name.startsWith("strings")))
        }.toList()
        assertTrue(files.any { it.name == "strings.xml" })
        val offenders = files.filter { it.readText(Charsets.UTF_8).contains('\u23FB') }.map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }
}
