package de.hotelremote.quick

import de.hotelremote.Keys
import de.hotelremote.automation.automationDurationMs
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.sleeptimer.MAX_TIMER_AUTOMATION_MS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Belegung eines Knopfs in Widget oder Sperrbildschirm-Benachrichtigung. */
@Serializable
sealed interface QuickButton {
    /** Eine Taste der Fernbedienung (kanonisch oder `X:<Label>`); [Keys.POWER] weicht wie in der App auf Ein/Aus aus. */
    @Serializable
    @SerialName("key")
    data class Key(val key: String) : QuickButton

    /** Die in „Schnellsteuerung“ gewählte Eingangstaste (Standard HDMI 1, sonst SOURCE). */
    @Serializable
    @SerialName("input")
    data object Input : QuickButton

    /** Automation der Fernbedienung; [name] für die Beschriftung und für gleich benannte auf anderen Fernbedienungen. */
    @Serializable
    @SerialName("automation")
    data class Run(val id: String, val name: String) : QuickButton
}

/** Was ein Knopfdruck bei der aktuellen Fernbedienung tut. */
sealed interface QuickPlan {
    data class Send(val signal: IrSignal) : QuickPlan

    /** [keys]: Signale der Fernbedienung, zu der die Automation gehört. */
    data class Run(val automation: Automation, val keys: Map<String, IrSignal>) : QuickPlan

    data class Fail(val result: QuickResult) : QuickPlan
}

/** Standardbelegung, Grenzen und Speicherformat (JSON) der Knöpfe. */
object QuickButtons {
    val DEFAULT: List<QuickButton> = listOf(QuickButton.Key(Keys.POWER), QuickButton.Key(Keys.VOL_DOWN), QuickButton.Key(Keys.VOL_UP), QuickButton.Input)

    /** Höchstzahl der Knöpfe; so viele Plätze haben widget_quick*.xml und notification_quick.xml. */
    const val MAX_WIDGET = 6
    const val MAX_NOTIFICATION = 5

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(QuickButton.serializer())

    fun encode(button: QuickButton): String = json.encodeToString(QuickButton.serializer(), button)

    fun decode(text: String?): QuickButton? =
        text?.let { runCatching { json.decodeFromString(QuickButton.serializer(), it) }.getOrNull() }

    fun encodeList(buttons: List<QuickButton>): String = json.encodeToString(listSerializer, buttons)

    /** null bei ungültigem Text oder leerer Liste. */
    fun decodeList(text: String?): List<QuickButton>? =
        text?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }?.takeIf { it.isNotEmpty() }

    /** 1 bis [max] Knöpfe; eine leere Liste wird zur Standardbelegung. */
    fun normalize(buttons: List<QuickButton>, max: Int): List<QuickButton> =
        buttons.take(max).ifEmpty { DEFAULT.take(max) }
}

/** Die Tile-Aktionen als Knopf (gleiches Verhalten wie im Widget). */
val QuickAction.button: QuickButton
    get() = when (this) {
        QuickAction.POWER -> QuickButton.Key(Keys.POWER)
        QuickAction.VOL_DOWN -> QuickButton.Key(Keys.VOL_DOWN)
        QuickAction.VOL_UP -> QuickButton.Key(Keys.VOL_UP)
        QuickAction.INPUT -> QuickButton.Input
    }

/**
 * Passt die Automation in die Zeit eines Broadcast-Receivers? Gleiche Grenze wie beim Sleep-Timer
 * (`goAsync()`, fertig nach etwa 10 s).
 */
fun fitsQuickControls(steps: List<AutomationStep>) = automationDurationMs(steps) <= MAX_TIMER_AUTOMATION_MS

/** Automation zu [button] bei [remote]: erst nach id, sonst nach Namen. */
fun findAutomation(button: QuickButton.Run, remote: SavedRemote): Automation? =
    remote.automations.firstOrNull { it.id == button.id } ?: remote.automations.firstOrNull { it.name == button.name }

/** Taste, die [button] bei diesen [keys] sendet; null, wenn sie fehlt (nicht für Automationen). */
fun buttonKey(button: QuickButton, keys: Set<String>, inputKey: String?): String? = when (button) {
    is QuickButton.Key -> if (button.key == Keys.POWER) quickKey(QuickAction.POWER, keys, inputKey) else button.key.takeIf { it in keys }
    QuickButton.Input -> quickKey(QuickAction.INPUT, keys, inputKey)
    is QuickButton.Run -> null
}

/** Löst einen Knopf für die zuletzt benutzte Fernbedienung auf; [inputKey] ist die gewählte Eingangstaste. */
fun resolveQuickButton(button: QuickButton, remote: SavedRemote, inputKey: String?): QuickPlan {
    if (button is QuickButton.Run) {
        val automation = findAutomation(button, remote) ?: return QuickPlan.Fail(QuickResult.AUTOMATION_MISSING)
        if (automation.steps.none { it.key in remote.keys }) return QuickPlan.Fail(QuickResult.KEY_MISSING)
        if (!fitsQuickControls(automation.steps)) return QuickPlan.Fail(QuickResult.TOO_LONG)
        return QuickPlan.Run(automation, remote.keys)
    }
    val key = buttonKey(button, remote.keys.keys, inputKey) ?: return QuickPlan.Fail(QuickResult.KEY_MISSING)
    return QuickPlan.Send(remote.keys.getValue(key))
}
