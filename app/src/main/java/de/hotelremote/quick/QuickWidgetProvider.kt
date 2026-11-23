package de.hotelremote.quick

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import de.hotelremote.R
import de.hotelremote.data.SavedRemote

/**
 * Startbildschirm-Widget: Name der zuletzt benutzten Fernbedienung und 1–6 runde Knöpfe, eigene Belegung je Widget
 * (eingerichtet in [WidgetConfigActivity], gespeichert in [QuickSettings]).
 */
class QuickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val remote = QuickControls.lastRemote(context)
        for (id in ids) manager.updateAppWidget(id, views(context, remote, id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val settings = QuickControls.settings(context)
        for (id in ids) settings.removeWidget(id)
    }

    companion object {
        /** Widgets dieser App auf dem Startbildschirm. */
        fun widgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
            return manager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java)) ?: IntArray(0)
        }

        /** Alle Widgets dieser App neu aufbauen (z. B. nach Wechsel der Fernbedienung). */
        fun updateAll(context: Context, remote: SavedRemote?) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            for (id in widgetIds(context)) manager.updateAppWidget(id, views(context, remote, id))
        }

        /** Ein Widget neu aufbauen (nach dem Einrichten; vor Android 12 ruft das System dann kein onUpdate). */
        fun update(context: Context, widgetId: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.updateAppWidget(widgetId, views(context, QuickControls.lastRemote(context), widgetId))
        }

        /** Bis 4 Knöpfe 48 dp, bei 5–6 kleinere, damit sie in 4 Zellen passen. */
        @VisibleForTesting
        internal fun layoutFor(count: Int) = if (count > 4) R.layout.widget_quick_dense else R.layout.widget_quick

        private fun views(context: Context, remote: SavedRemote?, widgetId: Int): RemoteViews {
            val buttons = QuickControls.settings(context).widgetButtons(widgetId)
            return RemoteViews(context.packageName, layoutFor(buttons.size)).apply {
                setTextViewText(R.id.quick_name, remote?.title ?: context.getString(R.string.quick_no_remote))
                setOnClickPendingIntent(R.id.quick_name, openAppIntent(context))
                bindQuickButtons(context, buttons, QuickButtons.MAX_WIDGET, "widget/$widgetId")
            }
        }
    }
}
