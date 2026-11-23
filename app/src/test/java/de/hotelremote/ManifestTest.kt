package de.hotelremote

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Prüft das Manifest direkt als XML: was Google Play daraus liest (Gerätefilter, Berechtigungen). */
class ManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    private fun elements(tag: String): List<Element> =
        doc.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    /** Play bietet die App nur Handys mit Infrarot-Sender an. */
    @Test fun consumerIrRequired() {
        val ir = elements("uses-feature").single { it.getAttributeNS(android, "name") == "android.hardware.consumerir" }
        assertEquals("true", ir.getAttributeNS(android, "required"))
    }

    /** Keine neuen Berechtigungen, insbesondere kein INTERNET (Datensicherheit „keine Daten“). */
    @Test fun permissionsUnchanged() {
        assertEquals(
            setOf(
                "android.permission.TRANSMIT_IR",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.VIBRATE",
            ),
            elements("uses-permission").map { it.getAttributeNS(android, "name") }.toSet(),
        )
    }
}
