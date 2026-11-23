package de.hotelremote.automation

import de.hotelremote.Keys
import de.hotelremote.data.AutomationStep

/** Herkunft einer Vorlage (siehe `docs/sleep-timer-research.md`); keine ist auf Geräten geprüft. */
enum class Confidence { MANUAL, FORUM, INFERRED }

/** Typ A: eigene Sleep-Taste mehrmals drücken; Typ B: Menüweg der Marke. */
enum class TemplateKind { SLEEP_KEY, MENU_PATH }

/** Zusätzlicher Hinweis im Editor. */
enum class TemplateNote {
    /** Der Fernseher zeigt den eingestellten Wert an. */
    SLEEP_DISPLAY,

    /** Menüs unterscheiden sich je Version bzw. merken sich die letzte Position. */
    MENU_POSITION,
}

/**
 * Vorlage für eine Automation, die den Sleep-Timer des Fernsehers stellt. [label] ist sprachneutral:
 * bei Typ A die angenommenen Stufen („10, 20, 30, …“), bei Typ B die Plattform („LG webOS“).
 * [brands] leer = jede Marke; [requiredKeys] müssen alle auf der Fernbedienung vorhanden sein.
 */
data class AutomationTemplate(
    val id: String,
    val kind: TemplateKind,
    val label: String,
    val minutes: Int,
    val brands: Set<String>,
    val requiredKeys: Set<String>,
    val steps: List<AutomationStep>,
    val confidence: Confidence,
    val note: TemplateNote?,
) {
    /** Typ A: wie oft die Sleep-Taste gedrückt wird. */
    val presses: Int get() = steps.size
}

/** Vorlagen für die aktive Fernbedienung ([matching]: Typ A, dann Typ B der Marke) und für andere Marken. */
data class TemplateSections(val matching: List<AutomationTemplate>, val others: List<AutomationTemplate>)

/** Katalog der Sleep-Timer-Vorlagen als feste Daten, ohne Netzabfrage. Tastenzahlen sind abgeleitet. */
object SleepTemplates {
    /** Sleep-Tasten in absteigender Priorität (`X:Picture Sleep` schaltet nur das Bild ab und fehlt bewusst). */
    val SLEEP_KEYS = listOf("X:Sleep", "X:Sleep Timer", "X:Timer", "X:Off Timer", "X:Sleepcall", "X:Timer Select", "X:Timer Set")

    /** Zielwerte der Typ-A-Vorlagen (Minuten). */
    val SLEEP_MINUTES = listOf(30, 60, 90)

    const val PRESS_PAUSE_MS = 800L
    const val AFTER_LAST_PRESS_MS = 3000L
    private const val OPEN_MS = 2500L
    private const val ENTER_MS = 1500L
    private const val NAV_MS = 800L
    private const val CLOSE_MS = 1000L

    /** Werte, die die Sleep-Taste nacheinander einstellt (nach „Aus“). */
    private class SleepCycle(val values: List<Int>, val confidence: Confidence, val uniform: Boolean)

    private val TEN = SleepCycle((1..12).map { it * 10 }, Confidence.INFERRED, uniform = true)
    private val THIRTY = SleepCycle((1..6).map { it * 30 }, Confidence.INFERRED, uniform = true)

    /** Vestel-Familie wie in `tools/irdata/brands.py`, dazu Grundig, Sharp, Hisense, Medion: 10-min-Schritte. */
    private val TEN_MINUTE_BRANDS = listOf(
        "Vestel", "Telefunken", "Finlux", "JVC", "Hitachi", "Toshiba", "Bush", "Techwood", "Digihome", "Luxor", "Polaroid",
        "Grundig", "Sharp", "Hisense", "Medion",
    )

    private val CYCLES: Map<String, SleepCycle> = buildMap {
        TEN_MINUTE_BRANDS.forEach { put(brandKey(it), TEN) }
        put("lg", SleepCycle(listOf(10, 20, 30, 60, 90, 120, 180, 240), Confidence.INFERRED, uniform = false))
        put("sony", SleepCycle(listOf(15, 30, 45, 60, 90, 120), Confidence.MANUAL, uniform = false))
        put("philips", SleepCycle(listOf(15, 30, 45, 60, 90, 120, 180, 240), Confidence.MANUAL, uniform = false))
        put("samsung", THIRTY)
        put("panasonic", THIRTY)
    }

    /** Vergleichsschlüssel einer Marke: nur Kleinbuchstaben und Ziffern (wie `brands.py`). */
    private fun brandKey(raw: String) = raw.lowercase().filter { it.isLetterOrDigit() }

    /** Schlüssel des vollständigen Namens und des ersten Worts („Samsung Electronics“ → samsung). */
    private fun brandKeys(raw: String) = setOf(brandKey(raw), brandKey(raw.trim().substringBefore(' ')))

    private fun cycle(brand: String): SleepCycle = brandKeys(brand).firstNotNullOfOrNull { CYCLES[it] } ?: TEN

    fun matchesBrand(template: AutomationTemplate, brand: String): Boolean =
        template.brands.isEmpty() || template.brands.any { brandKey(it) in brandKeys(brand) }

    /** Sleep-Taste der Fernbedienung nach Priorität, ohne Rücksicht auf Groß/Klein; null = keine. */
    fun sleepKey(keys: Set<String>): String? = SLEEP_KEYS.firstNotNullOfOrNull { wanted ->
        keys.firstOrNull { Keys.isExtra(it) && it.equals(wanted, ignoreCase = true) }
    }

    /** Drücke der Sleep-Taste bis [minutes] bei dieser Marke; null, wenn der Wert nicht im Zyklus liegt. */
    fun presses(brand: String, minutes: Int): Int? = cycle(brand).values.indexOf(minutes).takeIf { it >= 0 }?.plus(1)

    private fun sleepKeyTemplates(brand: String, keys: Set<String>): List<AutomationTemplate> {
        val key = sleepKey(keys) ?: return emptyList()
        val c = cycle(brand)
        val label = if (c.uniform) c.values.take(3).joinToString(", ") + ", …" else c.values.joinToString(", ")
        return SLEEP_MINUTES.mapNotNull { minutes ->
            val n = presses(brand, minutes) ?: return@mapNotNull null
            AutomationTemplate(
                id = "sleep_key_$minutes",
                kind = TemplateKind.SLEEP_KEY,
                label = label,
                minutes = minutes,
                brands = emptySet(),
                requiredKeys = setOf(key),
                steps = List(n) { i -> AutomationStep(key, if (i == n - 1) AFTER_LAST_PRESS_MS else PRESS_PAUSE_MS) },
                confidence = c.confidence,
                note = TemplateNote.SLEEP_DISPLAY,
            )
        }
    }

    // ---- Menüwege (Typ B) ----

    /** Öffnet das Einstellungsmenü: [key], danach ggf. weitere Schritte (z. B. HOME → LEFT … → OK). */
    private class Opener(val key: String, val then: List<AutomationStep> = emptyList())

    private class MenuRecipe(
        val id: String,
        val label: String,
        val brand: String,
        /** Erste vorhandene Variante gewinnt. */
        val openers: List<Opener>,
        val path: List<AutomationStep>,
        val minutes: Int,
        val confidence: Confidence,
        val note: TemplateNote? = null,
    )

    private fun nav(key: String, times: Int = 1) = List(times) { AutomationStep(key, NAV_MS) }
    private fun enter() = listOf(AutomationStep(Keys.OK, ENTER_MS))
    private fun open(key: String) = Opener(key)

    private val SAMSUNG_OPENERS = listOf(
        open("X:Settings"), open("X:Menu"), open(Keys.MENU),
        // Smart Hub: das Zahnrad ist ganz links; Überschießen nach links landet sicher dort.
        Opener(Keys.HOME, nav(Keys.LEFT, 8) + enter()),
    )
    private val LG_OPENERS = listOf("X:Settings", "X:Q Settings", "X:Q.Settings", "X:Q.Menu", "X:Q Menu").map(::open)

    private val RECIPES: List<MenuRecipe> = listOf(
        // Settings > General > System Manager > Time > Sleep Timer > 60.
        MenuRecipe(
            "samsung_tizen_2021", "Samsung Tizen ≤ 2021", "Samsung", SAMSUNG_OPENERS,
            nav(Keys.DOWN, 3) + enter() + nav(Keys.DOWN, 2) + enter() + enter() + nav(Keys.DOWN) + enter() +
                nav(Keys.DOWN, 2) + enter(),
            60, Confidence.MANUAL,
        ),
        // Settings > All Settings > General & Privacy > System Manager > Time > Sleep Timer > 60.
        MenuRecipe(
            "samsung_tizen_2022", "Samsung Tizen 2022+", "Samsung", SAMSUNG_OPENERS,
            enter() + nav(Keys.DOWN, 3) + enter() + nav(Keys.DOWN) + enter() + enter() + nav(Keys.DOWN) + enter() +
                nav(Keys.DOWN, 2) + enter(),
            60, Confidence.MANUAL,
        ),
        // TOOLS > Sleep Timer, Wert mit RIGHT (Aus → 30 → 60).
        MenuRecipe(
            "samsung_tools", "Samsung (TOOLS)", "Samsung", listOf(open("X:Tools")),
            nav(Keys.DOWN, 3) + nav(Keys.RIGHT, 2) + enter(),
            60, Confidence.MANUAL,
        ),
        // Quick Settings: Sleep-Timer-Symbol, OK schaltet Aus → 10 → 20 → 30 → 60.
        MenuRecipe(
            "lg_webos", "LG webOS", "LG", LG_OPENERS,
            nav(Keys.DOWN, 3) + nav(Keys.OK, 4),
            60, Confidence.MANUAL, TemplateNote.MENU_POSITION,
        ),
        // HOME > Settings (unten) > System Settings > Clock/Timers > Sleep Timer > 60.
        MenuRecipe(
            "sony_bravia", "Sony Bravia 2012–2016", "Sony", listOf(open(Keys.HOME)),
            nav(Keys.DOWN, 8) + enter() + enter() + enter() + enter() + nav(Keys.DOWN, 4) + enter(),
            60, Confidence.MANUAL,
        ),
        // Quick-Settings-Taste > Sleep-Timer-Kachel > 60.
        MenuRecipe(
            "sony_google_tv", "Sony Google TV", "Sony", listOf(open("X:Quick Settings"), open("X:Settings")),
            nav(Keys.RIGHT, 3) + enter() + nav(Keys.DOWN, 4) + enter(),
            60, Confidence.FORUM, TemplateNote.MENU_POSITION,
        ),
        // Settings-Taste > Sleep timer, Schieberegler in 5-min-Schritten: 12 × RIGHT = 60.
        MenuRecipe(
            "philips_saphi", "Philips Saphi", "Philips", listOf(open("X:Settings"), open("X:Setup")),
            nav(Keys.DOWN, 7) + enter() + nav(Keys.RIGHT, 12) + enter(),
            60, Confidence.MANUAL, TemplateNote.MENU_POSITION,
        ),
        // MENU > Timer > Off Timer > 30 (laut Handbuch).
        MenuRecipe(
            "panasonic_viera", "Panasonic Viera", "Panasonic", listOf(open(Keys.MENU)),
            nav(Keys.DOWN, 3) + enter() + enter() + nav(Keys.UP, 2) + enter(),
            30, Confidence.MANUAL,
        ),
        // Settings > System > Power & Energy > Power > Sleep timer > 60 (10-min-Schritte).
        MenuRecipe(
            "hisense_vidaa", "Hisense VIDAA", "Hisense", listOf(open("X:Settings"), open("X:Nav Setting"), open(Keys.MENU)),
            nav(Keys.DOWN, 5) + enter() + nav(Keys.DOWN, 3) + enter() + enter() + nav(Keys.DOWN) + enter() +
                nav(Keys.DOWN, 6) + enter(),
            60, Confidence.MANUAL, TemplateNote.MENU_POSITION,
        ),
        // Zahnrad > System > Power & Energy > Sleep timer > 60.
        MenuRecipe(
            "tcl_google_tv", "TCL Google TV", "TCL", listOf(open("X:Settings"), open(Keys.MENU)),
            nav(Keys.DOWN, 6) + enter() + nav(Keys.DOWN, 3) + enter() + nav(Keys.DOWN) + enter() +
                nav(Keys.DOWN, 6) + enter(),
            60, Confidence.MANUAL, TemplateNote.MENU_POSITION,
        ),
    )

    /** Definierter Startzustand: EXIT, BACK, EXIT – mit dem, was die Fernbedienung davon hat. */
    private fun startState(keys: Set<String>): List<AutomationStep> {
        val exit = Keys.EXIT in keys
        val back = Keys.BACK in keys
        return when {
            exit && back -> nav(Keys.EXIT) + nav(Keys.BACK) + nav(Keys.EXIT)
            exit -> nav(Keys.EXIT, 2)
            back -> nav(Keys.BACK, 2)
            else -> emptyList()
        }
    }

    private fun close(keys: Set<String>): List<AutomationStep> = when {
        Keys.EXIT in keys -> listOf(AutomationStep(Keys.EXIT, CLOSE_MS))
        Keys.BACK in keys -> listOf(AutomationStep(Keys.BACK, CLOSE_MS))
        else -> emptyList()
    }

    private fun build(r: MenuRecipe, opener: Opener, keys: Set<String>): AutomationTemplate {
        val core = listOf(AutomationStep(opener.key, OPEN_MS)) + opener.then + r.path
        return AutomationTemplate(
            id = r.id,
            kind = TemplateKind.MENU_PATH,
            label = r.label,
            minutes = r.minutes,
            brands = setOf(r.brand),
            requiredKeys = core.map { it.key }.toSet(),
            steps = startState(keys) + core + close(keys),
            confidence = r.confidence,
            note = r.note,
        )
    }

    /** Menüwege aller Marken, für die die Fernbedienung alle nötigen Tasten hat (erste passende Variante). */
    private fun menuTemplates(keys: Set<String>): List<AutomationTemplate> = RECIPES.mapNotNull { r ->
        r.openers.asSequence().map { build(r, it, keys) }.firstOrNull { keys.containsAll(it.requiredKeys) }
    }

    /** Jeder Menüweg in jeder Variante, mit vollem Startzustand (für Prüfungen des Katalogs). */
    fun allMenuTemplates(): List<AutomationTemplate> {
        val keys = setOf(Keys.EXIT, Keys.BACK)
        return RECIPES.flatMap { r -> r.openers.map { build(r, it, keys) } }
    }

    /** Vorlagen für eine Fernbedienung der Marke [brand] mit den Tasten [keys]. */
    fun forRemote(brand: String, keys: Set<String>): TemplateSections {
        val (own, other) = menuTemplates(keys).partition { matchesBrand(it, brand) }
        return TemplateSections(sleepKeyTemplates(brand, keys) + own, other)
    }
}
