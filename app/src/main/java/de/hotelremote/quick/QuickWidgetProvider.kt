package de.hotelremote.quick

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import de.hotelremote.R
import de.hotelremote.data.SavedRemote

/** Startbildschirm-Widget: Name der zuletzt benutzten Fernbedienung und vier runde Knöpfe. */
class QuickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context, QuickControls.lastRemote(context)))
    }

    companion object {
        /** Alle Widgets dieser App neu aufbauen (z. B. nach Wechsel der Fernbedienung). */
        fun updateAll(context: Context, remote: SavedRemote?) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, remote))
        }

        private fun views(context: Context, remote: SavedRemote?) = RemoteViews(context.packageName, R.layout.widget_quick).apply {
            setTextViewText(R.id.quick_name, remote?.title ?: context.getString(R.string.quick_no_remote))
            setOnClickPendingIntent(R.id.quick_name, openAppIntent(context))
            bindQuickButtons(context)
        }
    }
}
