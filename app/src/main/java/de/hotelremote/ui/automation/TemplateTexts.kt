package de.hotelremote.ui.automation

import android.content.res.Resources
import de.hotelremote.R
import de.hotelremote.automation.AutomationTemplate
import de.hotelremote.automation.Confidence
import de.hotelremote.automation.TemplateKind
import de.hotelremote.automation.TemplateNote

/** Titel einer Vorlage, z. B. „Sleep-Taste 6× (≈ 60 min)“ bzw. „LG webOS: Menüweg (≈ 60 min)“. */
fun templateTitle(res: Resources, t: AutomationTemplate): String = when (t.kind) {
    TemplateKind.SLEEP_KEY -> res.getString(R.string.tpl_sleep_title, t.presses, t.minutes)
    TemplateKind.MENU_PATH -> res.getString(R.string.tpl_menu_title, t.label, t.minutes)
}

/** Zweite Zeile in der Liste: angenommene Stufen (Typ A) bzw. „ungeprüft“ (Typ B). */
fun templateSubtitle(res: Resources, t: AutomationTemplate): String = when (t.kind) {
    TemplateKind.SLEEP_KEY -> res.getString(R.string.tpl_sleep_assumption, t.label)
    TemplateKind.MENU_PATH -> res.getString(R.string.tpl_untested)
}

/** Vorgeschlagener Name der Automation. */
fun templateName(res: Resources, t: AutomationTemplate): String = res.getString(R.string.tpl_name, t.minutes)

/** Hinweise oben im Editor: Vertrauensstufe mit Quelle, dann ggf. die Notiz der Vorlage. */
fun templateNotices(res: Resources, t: AutomationTemplate): List<String> {
    val confidence = when {
        t.kind == TemplateKind.SLEEP_KEY && t.confidence == Confidence.MANUAL -> R.string.tpl_conf_manual_steps
        t.kind == TemplateKind.MENU_PATH && t.confidence == Confidence.MANUAL -> R.string.tpl_conf_manual_path
        t.kind == TemplateKind.MENU_PATH && t.confidence == Confidence.FORUM -> R.string.tpl_conf_forum_path
        else -> R.string.tpl_conf_guess
    }
    val note = when (t.note) {
        TemplateNote.SLEEP_DISPLAY -> R.string.tpl_note_display
        TemplateNote.MENU_POSITION -> R.string.tpl_note_position
        null -> null
    }
    return listOfNotNull(res.getString(confidence), note?.let(res::getString))
}
