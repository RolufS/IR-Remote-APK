package de.hotelremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Jede Übersetzung hat genau die String-Namen und Formatargumente von `values/strings.xml`. */
class TranslationsTest {
    private val res = File("src/main/res")

    private fun strings(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as org.w3c.dom.Element
            e.getAttribute("name") to e.textContent
        }
    }

    /** Formatargumente wie `%1$s`, sortiert; ein `%%` zählt nicht. */
    private fun formatArgs(text: String): List<String> =
        Regex("""%(?:\d+\$)?[a-zA-Z%]""").findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()

    @Test fun everyLanguageHasSameNamesAndFormatArgs() {
        val base = strings("values")
        assertEquals("Ordner values-*", LANGUAGE_DIRS.sorted(), translationDirs())
        for (dir in LANGUAGE_DIRS) {
            val tr = strings(dir)
            assertEquals("Namen in $dir", base.keys.sorted(), tr.keys.sorted())
            for ((name, text) in base) {
                assertEquals("Formatargumente von $name in $dir", formatArgs(text), formatArgs(tr.getValue(name)))
                assertTrue("$name in $dir leer", tr.getValue(name).isNotBlank())
            }
        }
    }

    private fun translationDirs() =
        res.listFiles { f -> f.isDirectory && File(f, "strings.xml").isFile && f.name != "values" }!!.map { it.name }.sorted()

    companion object {
        val LANGUAGE_DIRS = listOf("en", "zh-rCN", "hi", "es", "fr", "ar", "bn", "pt", "ru", "in").map { "values-$it" }
    }
}
