package de.hotelremote.quick

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import de.hotelremote.MainActivity
import de.hotelremote.R

/** IDs eines Knopf-Platzes in widget_quick*.xml und notification_quick.xml. */
private class Slot(val root: Int, val button: Int, val icon: Int, val label: Int)

private val SLOTS = listOf(
    Slot(R.id.quick_slot_1, R.id.quick_btn_1, R.id.quick_icon_1, R.id.quick_label_1),
    Slot(R.id.quick_slot_2, R.id.quick_btn_2, R.id.quick_icon_2, R.id.quick_label_2),
    Slot(R.id.quick_slot_3, R.id.quick_btn_3, R.id.quick_icon_3, R.id.quick_label_3),
    Slot(R.id.quick_slot_4, R.id.quick_btn_4, R.id.quick_icon_4, R.id.quick_label_4),
    Slot(R.id.quick_slot_5, R.id.quick_btn_5, R.id.quick_icon_5, R.id.quick_label_5),
    Slot(R.id.quick_slot_6, R.id.quick_btn_6, R.id.quick_icon_6, R.id.quick_label_6),
)

/** IDs der Knöpfe (für Tests): Platz i hat die Wurzel [slotRootId] und den Knopf [slotButtonId]. */
internal fun slotRootId(index: Int) = SLOTS[index].root

internal fun slotButtonId(index: Int) = SLOTS[index].button

internal fun slotLabelId(index: Int) = SLOTS[index].label

internal fun slotIconId(index: Int) = SLOTS[index].icon

internal val QuickTone.background: Int
    get() = when (this) {
        QuickTone.KEY -> R.drawable.quick_key_bg
        QuickTone.POWER -> R.drawable.quick_power_bg
        QuickTone.INPUT -> R.drawable.quick_input_bg
        QuickTone.RED -> R.drawable.quick_red_bg
        QuickTone.GREEN -> R.drawable.quick_green_bg
        QuickTone.YELLOW -> R.drawable.quick_yellow_bg
        QuickTone.BLUE -> R.drawable.quick_blue_bg
    }

/**
 * Belegt die ersten [slots] Plätze mit [buttons] und blendet die übrigen aus. [source] (z. B. "widget/12") macht
 * die PendingIntents je Widget und Platz eindeutig.
 */
internal fun RemoteViews.bindQuickButtons(context: Context, buttons: List<QuickButton>, slots: Int, source: String) {
    val res = context.resources
    SLOTS.take(slots).forEachIndexed { i, slot ->
        val button = buttons.getOrNull(i)
        if (button == null) {
            setViewVisibility(slot.root, View.GONE)
            return@forEachIndexed
        }
        val look = quickButtonLook(res, button)
        setViewVisibility(slot.root, View.VISIBLE)
        setInt(slot.button, "setBackgroundResource", look.tone.background)
        setContentDescription(slot.button, look.description)
        if (look.icon != null) {
            setImageViewResource(slot.icon, look.icon)
            setViewVisibility(slot.icon, View.VISIBLE)
        } else {
            setViewVisibility(slot.icon, View.GONE)
        }
        if (look.label != null) {
            setTextViewText(slot.label, look.label)
            setTextViewTextSize(slot.label, TypedValue.COMPLEX_UNIT_SP, look.labelSp)
            setViewVisibility(slot.label, View.VISIBLE)
        } else {
            setViewVisibility(slot.label, View.GONE)
        }
        setOnClickPendingIntent(slot.button, QuickControlReceiver.pendingIntent(context, button, "$source/$i"))
    }
}

/** Öffnet die App (gleicher PendingIntent wie in der Sleep-Timer-Benachrichtigung). */
internal fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)
