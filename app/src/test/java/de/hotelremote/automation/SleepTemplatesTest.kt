package de.hotelremote.automation

import de.hotelremote.Keys
import de.hotelremote.data.AutomationStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTemplatesTest {
    private val nav = setOf(Keys.MENU, Keys.HOME, Keys.OK, Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT, Keys.BACK, Keys.EXIT)
    private val canonical = Keys::class.java.declaredFields
        .filter { it.type == String::class.java }
        .map { it.isAccessible = true; it.get(null) as String }
        .toSet()

    // ---- Sleep-Taste erkennen ----

    @Test fun sleepKeyFollowsPriority() {
        assertEquals("X:Sleep", SleepTemplates.sleepKey(setOf("X:Timer", "X:Sleep", "X:Sleep Timer")))
        assertEquals("X:Sleep Timer", SleepTemplates.sleepKey(setOf("X:Timer", "X:Sleep Timer")))
        assertEquals("X:Timer", SleepTemplates.sleepKey(setOf("X:Off Timer", "X:Timer")))
        assertEquals("X:Off Timer", SleepTemplates.sleepKey(setOf("X:Sleepcall", "X:Off Timer")))
        assertEquals("X:Sleepcall", SleepTemplates.sleepKey(setOf("X:Timer Select", "X:Sleepcall")))
        assertEquals("X:Timer Select", SleepTemplates.sleepKey(setOf("X:Timer Set", "X:Timer Select")))
        assertEquals("X:Timer Set", SleepTemplates.sleepKey(setOf("X:Timer Set")))
        // Groß/Klein egal, die Taste bleibt wie gespeichert.
        assertEquals("X:SLEEP", SleepTemplates.sleepKey(setOf("X:SLEEP")))
    }

    @Test fun pictureSleepIsNoSleepKey() {
        assertNull(SleepTemplates.sleepKey(setOf("X:Picture Sleep", Keys.MENU)))
        assertNull(SleepTemplates.sleepKey(setOf("Sleep")))
    }

    // ---- Drückanzahl ----

    @Test fun pressesPerBrandAndTarget() {
        assertEquals(6, SleepTemplates.presses("Vestel", 60))
        assertEquals(3, SleepTemplates.presses("Telefunken", 30))
        assertEquals(9, SleepTemplates.presses("Hisense", 90))
        assertEquals(4, SleepTemplates.presses("Sony", 60))
        assertEquals(2, SleepTemplates.presses("Sony", 30))
        assertEquals(4, SleepTemplates.presses("LG", 60))
        assertEquals(5, SleepTemplates.presses("lg", 90))
        assertEquals(4, SleepTemplates.presses("Philips", 60))
        assertEquals(2, SleepTemplates.presses("Samsung", 60))
        assertEquals(3, SleepTemplates.presses("Panasonic", 90))
        assertEquals(6, SleepTemplates.presses("Unbekannt", 60))
        // Marke wie vom Nutzer eingegeben: Groß/Klein, Zusätze.
        assertEquals(2, SleepTemplates.presses("SAMSUNG Electronics", 60))
    }

    @Test fun sleepKeyTemplatesPressWithShortPausesAndWaitAfterTheLast() {
        val t = SleepTemplates.forRemote("Vestel", setOf("X:Sleep", Keys.OK)).matching
        assertEquals(listOf(30, 60, 90), t.map { it.minutes })
        assertTrue(t.all { it.kind == TemplateKind.SLEEP_KEY && it.brands.isEmpty() })
        val sixty = t[1]
        assertEquals(setOf("X:Sleep"), sixty.requiredKeys)
        assertEquals(List(5) { AutomationStep("X:Sleep", 800) } + AutomationStep("X:Sleep", 3000), sixty.steps)
        assertEquals(TemplateNote.SLEEP_DISPLAY, sixty.note)
        assertEquals(Confidence.INFERRED, sixty.confidence)
        assertEquals(Confidence.MANUAL, SleepTemplates.forRemote("Sony", setOf("X:Sleep")).matching.first().confidence)
    }

    @Test fun noSleepKeyNoSleepKeyTemplates() {
        assertTrue(SleepTemplates.forRemote("Vestel", nav).matching.none { it.kind == TemplateKind.SLEEP_KEY })
    }

    // ---- Menüwege: Marke und vorhandene Tasten ----

    @Test fun menuTemplatesOfTheBrandComeAfterTheSleepKey() {
        val s = SleepTemplates.forRemote("Samsung", nav + "X:Sleep" + "X:Tools")
        assertEquals(List(3) { TemplateKind.SLEEP_KEY }, s.matching.take(3).map { it.kind })
        val menu = s.matching.drop(3)
        assertTrue(menu.isNotEmpty())
        assertTrue(menu.all { it.kind == TemplateKind.MENU_PATH && it.brands == setOf("Samsung") })
        assertEquals(setOf("samsung_tizen_2021", "samsung_tizen_2022", "samsung_tools"), menu.map { it.id }.toSet())
        assertTrue(s.others.none { "Samsung" in it.brands })
    }

    @Test fun menuTemplatesNeedAllRequiredKeys() {
        // Ohne TOOLS kein Weg über TOOLS.
        assertTrue(SleepTemplates.forRemote("Samsung", nav).matching.none { it.id == "samsung_tools" })
        // LG webOS braucht eine Settings-/Q.Menu-Taste.
        assertTrue(SleepTemplates.forRemote("LG", nav).matching.isEmpty())
        val lg = SleepTemplates.forRemote("LG", nav + "X:Q Menu").matching.single()
        assertEquals("lg_webos", lg.id)
        assertTrue("X:Q Menu" in lg.requiredKeys)
        assertEquals(AutomationStep("X:Q Menu", 2500), lg.steps[3])
        // Andere Marken nur, wenn alle Tasten vorhanden sind.
        val others = SleepTemplates.forRemote("Vestel", nav).others
        assertTrue(others.isNotEmpty())
        assertTrue(others.all { nav.containsAll(it.requiredKeys) })
        assertTrue(others.none { it.id == "lg_webos" || it.id == "samsung_tools" })
        assertTrue(SleepTemplates.forRemote("Vestel", setOf(Keys.POWER)).others.isEmpty())
    }

    @Test fun firstAvailableOpenerWins() {
        val keys = nav + "X:Settings"
        val tizen = SleepTemplates.forRemote("Samsung", keys).matching.first { it.id == "samsung_tizen_2022" }
        assertEquals("X:Settings", tizen.steps[3].key)
        val viaMenu = SleepTemplates.forRemote("Samsung", nav).matching.first { it.id == "samsung_tizen_2022" }
        assertEquals(Keys.MENU, viaMenu.steps[3].key)
        // Ohne MENU über HOME.
        val viaHome = SleepTemplates.forRemote("Samsung", nav - Keys.MENU).matching.first { it.id == "samsung_tizen_2022" }
        assertEquals(Keys.HOME, viaHome.steps[3].key)
        assertFalse(Keys.MENU in viaHome.requiredKeys)
    }

    @Test fun menuPathStartsFromAKnownStateAndCloses() {
        val t = SleepTemplates.forRemote("Panasonic", nav).matching.single { it.id == "panasonic_viera" }
        assertEquals(listOf(Keys.EXIT, Keys.BACK, Keys.EXIT, Keys.MENU), t.steps.take(4).map { it.key })
        assertEquals(Keys.EXIT, t.steps.last().key)
        assertEquals(30, t.minutes)
        // Ohne BACK: zweimal EXIT; ohne EXIT: zum Schluss BACK.
        val noBack = SleepTemplates.forRemote("Panasonic", nav - Keys.BACK).matching.single { it.id == "panasonic_viera" }
        assertEquals(listOf(Keys.EXIT, Keys.EXIT, Keys.MENU), noBack.steps.take(3).map { it.key })
        val noExit = SleepTemplates.forRemote("Panasonic", nav - Keys.EXIT).matching.single { it.id == "panasonic_viera" }
        assertEquals(listOf(Keys.BACK, Keys.BACK, Keys.MENU), noExit.steps.take(3).map { it.key })
        assertEquals(Keys.BACK, noExit.steps.last().key)
    }

    @Test fun philipsSliderGoesRightTwelveTimes() {
        val t = SleepTemplates.forRemote("Philips", nav + "X:Settings").matching.single { it.id == "philips_saphi" }
        assertEquals(12, t.steps.count { it.key == Keys.RIGHT })
        assertEquals(60, t.minutes)
    }

    // ---- Katalog insgesamt ----

    @Test fun everyTemplateUsesKnownKeysAndAllowedWaits() {
        val all = SleepTemplates.allMenuTemplates() +
            listOf("Vestel", "LG", "Sony", "Philips", "Samsung", "Panasonic", "x").flatMap {
                SleepTemplates.forRemote(it, setOf("X:Sleep")).matching
            }
        assertTrue(all.size > 20)
        for (t in all) {
            assertTrue("${t.id}: leer", t.steps.isNotEmpty())
            for (s in t.steps) {
                assertTrue("${t.id}: Taste ${s.key}", s.key in canonical || (Keys.isExtra(s.key) && Keys.extraLabel(s.key).isNotBlank()))
                assertTrue("${t.id}: Wartezeit ${s.delayAfterMs}", s.delayAfterMs in AutomationStep.MIN_DELAY_MS..AutomationStep.MAX_DELAY_MS)
            }
            // Pflicht sind alle Tasten außer dem optionalen Startzustand/Schließen (EXIT/BACK).
            val used = t.steps.map { it.key }.toSet()
            assertTrue("${t.id}: requiredKeys ${t.requiredKeys}", used.containsAll(t.requiredKeys))
            assertTrue("${t.id}: requiredKeys ${t.requiredKeys}", t.requiredKeys.containsAll(used - Keys.EXIT - Keys.BACK))
        }
        // Jeder Menüweg gehört zu einer Marke.
        assertTrue(SleepTemplates.allMenuTemplates().all { it.brands.isNotEmpty() })
        assertEquals(
            setOf("samsung_tizen_2021", "samsung_tizen_2022", "samsung_tools", "lg_webos", "sony_bravia", "sony_google_tv",
                "philips_saphi", "panasonic_viera", "hisense_vidaa", "tcl_google_tv"),
            SleepTemplates.allMenuTemplates().map { it.id }.toSet(),
        )
    }
}
