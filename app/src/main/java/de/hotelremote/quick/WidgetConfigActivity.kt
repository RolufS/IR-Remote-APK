package de.hotelremote.quick

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import de.hotelremote.ui.quick.QuickButtonsEditor
import de.hotelremote.ui.quick.QuickButtonsTarget
import de.hotelremote.ui.theme.HotelRemoteTheme

/**
 * Knöpfe eines Widgets einrichten: beim Hinzufügen (`android:configure`), über „Widget bearbeiten“ des Launchers
 * (ab Android 12) und aus „Schnellsteuerung“. Ergebnis wie vom System verlangt: RESULT_OK bzw. RESULT_CANCELED,
 * jeweils mit EXTRA_APPWIDGET_ID. AppCompatActivity für die App-Sprache vor Android 13.
 */
class WidgetConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Zurück oder Abbrechen: das System entfernt ein gerade hinzugefügtes Widget (vor Android 12 immer).
        setResult(RESULT_CANCELED, result(widgetId))
        // Exportiert (der Launcher startet sie): nur Widgets dieser App einrichten.
        if (widgetId !in QuickWidgetProvider.widgetIds(this)) {
            finish()
            return
        }
        val settings = QuickControls.settings(this)
        val remote = QuickControls.lastRemote(this)
        val initial = settings.widgetButtons(widgetId)
        setContent {
            HotelRemoteTheme {
                QuickButtonsEditor(
                    target = QuickButtonsTarget.WIDGET,
                    remote = remote,
                    inputKey = settings.inputKey,
                    initial = initial,
                    onSave = { buttons ->
                        settings.setWidgetButtons(widgetId, buttons)
                        QuickWidgetProvider.update(this, widgetId)
                        setResult(RESULT_OK, result(widgetId))
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun result(widgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    companion object {
        /** Aus der App: vorhandenes Widget bearbeiten. */
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
}
