package de.hotelremote

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.automation.SleepTemplates
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.Brand
import de.hotelremote.data.CodeDatabase
import de.hotelremote.data.CodeRepository
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.quick.LockStyle
import de.hotelremote.quick.QuickButton
import de.hotelremote.quick.QuickControls
import de.hotelremote.quick.QuickNotification
import de.hotelremote.quick.QuickPrefs
import de.hotelremote.quick.QuickWidgetEntry
import de.hotelremote.quick.QuickWidgetProvider
import de.hotelremote.sleeptimer.SleepAction
import de.hotelremote.sleeptimer.SleepTimer
import de.hotelremote.ui.automation.AutomationListScreen
import de.hotelremote.ui.automation.RecordingBarState
import de.hotelremote.ui.language.AppLanguages
import de.hotelremote.ui.language.LanguageScreen
import de.hotelremote.ui.quick.QuickButtonsEditor
import de.hotelremote.ui.quick.QuickButtonsTarget
import de.hotelremote.ui.quick.QuickControlsScreen
import de.hotelremote.ui.remote.KeyHandler
import de.hotelremote.ui.remote.QuickSheetContent
import de.hotelremote.ui.remote.RemoteScreen
import de.hotelremote.ui.sleeptimer.SleepTimerDialog
import de.hotelremote.ui.theme.HotelRemoteTheme
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.ui.wizard.WizardScreen
import de.hotelremote.wizard.WizardState
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Rendert Screenshots (1080 x 1920) aus den echten Compose-Bildschirmen: die vier Play-Store-Bilder nach
 * `docs/play-store/` (Eigenschaft [DIR_PROPERTY]) und die Bilder der GitHub-Seite nach `docs/screenshots/` (daneben).
 * Läuft nur mit `-Pscreenshots`, sonst übersprungen:
 * `./gradlew :app:testDebugUnitTest --tests de.hotelremote.StoreScreenshots -Pscreenshots`
 *
 * Beispieldaten: die Samsung-Codes aus der mitgelieferten codes.db, gespeichert als „Hotel Berlin · Samsung“.
 * Sperrbildschirm und Widget sind Darstellungen: die echten RemoteViews-Layouts der App in einem nachgebildeten Rahmen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w360dp-h640dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshots {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var storeDir: File
    private lateinit var docsDir: File
    private lateinit var brands: List<Brand>
    private lateinit var remote: SavedRemote

    @Before fun setUp() {
        val dir = System.getProperty(DIR_PROPERTY)
        assumeTrue("Nur mit -Pscreenshots", dir != null)
        storeDir = File(dir!!).apply { mkdirs() }
        docsDir = File(storeDir.parentFile, "screenshots").apply { mkdirs() }
        CodeDatabase.open(app).use { db ->
            val repo = CodeRepository(db)
            brands = repo.brands()
            remote = sampleRemote(repo)
        }
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        QuickControls.reset()
    }

    @After fun tearDown() {
        if (::storeDir.isInitialized) QuickControls.reset()
    }

    private val samsung get() = brands.first { it.name == "Samsung" }

    /**
     * Gespeicherte Fernbedienung wie nach dem Wizard: der Samsung-Codesatz mit den meisten Tasten und HDMI-Eingängen
     * (erste Eingangsvariante), dazu drei Automationen.
     */
    private fun sampleRemote(repo: CodeRepository): SavedRemote {
        val brand = repo.brands().first { it.name == "Samsung" }
        val (keys, inputs) = repo.codeSetsForBrand(brand.id)
            .map { id -> repo.keys(id) to repo.inputVariants(id, brand.id).firstOrNull().orEmpty() }
            .maxBy { (keys, inputs) -> (if (Keys.HDMI1 in inputs || Keys.HDMI1 in keys) 1000 else 0) + keys.size }
        val all = keys + inputs
        assertTrue("HDMI 1 im Beispiel", Keys.HDMI1 in all)
        val automations = listOf(
            Automation("a1", "Netflix auf HDMI 2", listOf(AutomationStep(Keys.HDMI2, 3000), AutomationStep(Keys.OK, 1000))),
            Automation(
                "a2",
                "Leiser für die Nacht",
                List(5) { AutomationStep(Keys.VOL_DOWN, 500) },
            ),
            Automation("a3", "Nachrichten", listOf(AutomationStep("DIGIT_1", 500), AutomationStep("DIGIT_0", 500), AutomationStep(Keys.OK))),
        ).filter { a -> a.steps.all { it.key in all } }
        return SavedRemote(
            id = "r1", name = "Hotel Berlin · Samsung", brand = "Samsung", codeLabel = "Code 1",
            keys = all, lastUsed = 1L, automations = automations,
        )
    }

    // --- Play Store (docs/play-store/) und GitHub-Seite (docs/screenshots/) ---

    @Test fun remote() = shoot("01-remote.png", store = "screenshot-1-remote.png") { Remote() }

    @Test fun wizardBrand() = shoot("02-wizard-brand.png", store = "screenshot-2-wizard-brand.png") {
        Wizard(WizardState.PickBrand)
    }

    @Test fun wizardTest() = shoot("03-wizard-test.png", store = "screenshot-3-wizard-test.png") {
        Wizard(WizardState.TestCode(samsung.id, index = 2, total = samsung.codeSetCount, codeSetId = 0, testKey = Keys.MUTE))
    }

    @Test fun inputs() = shoot("04-wizard-inputs.png", store = "screenshot-4-inputs.png") {
        Wizard(WizardState.TestInputs(samsung.id, codeSetId = 0, variantIndex = 0, variantCount = 2))
    }

    // --- Nur GitHub-Seite ---

    /** ⚡-Sheet über der Fernbedienung; nachgebildet ohne ModalBottomSheet (eigenes Fenster, Animation). */
    @Test fun quickSheet() = shoot("05-quick-menu.png") {
        Box(Modifier.fillMaxSize()) {
            Remote()
            Box(Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.5f)))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(RemoteColors.BackgroundTop),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(vertical = 22.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(RemoteColors.KeyText.copy(alpha = 0.4f)),
                )
                val now = 1_000_000_000L
                QuickSheetContent(
                    automations = remote.automations,
                    progress = null,
                    timer = SleepTimer(now + 42 * 60_000L, remote.id, SleepAction.PowerOff, exact = true),
                    now = now,
                    onRun = {}, onCancelRun = {}, onTimer = {}, onCancelTimer = {}, onEdit = {},
                )
            }
        }
    }

    @Test fun automations() = shoot("06-automations.png") {
        AutomationListScreen(
            automations = remote.automations, progress = null, onRun = {}, onCancelRun = {}, onEdit = {}, onDelete = {},
            onBack = {}, templates = SleepTemplates.forRemote(remote.brand, remote.keys.keys),
        )
    }

    @Test fun recording() = shoot("07-recording.png") {
        Remote(recording = RecordingBarState(recordedCount = 3, hint = false, onUndo = {}, onDone = {}, onCancel = {}))
    }

    @Test fun sleepTimer() = shoot("08-sleep-timer.png") {
        Remote()
        SleepTimerDialog(remote, onStart = {}, onDismiss = {})
    }

    /** Nach unten gescrollt bis „Ganze Fernbedienung über dem Sperrbildschirm erlauben“. */
    @Test fun quickControls() = shoot("09-quick-controls.png", scrollTo = "quick_full_remote") {
        QuickControlsScreen(
            remote = remote,
            prefs = QuickPrefs(Keys.HDMI1, lockScreen = true, lockStyle = LockStyle.EXPANDED, fullRemote = true),
            onInputKey = {}, onLockScreen = {}, onBack = {},
            widgets = listOf(QuickWidgetEntry(1, 4), QuickWidgetEntry(2, 6)),
        )
    }

    @Test fun widgetSetup() = shoot("10-widget-setup.png") {
        QuickButtonsEditor(
            target = QuickButtonsTarget.WIDGET, remote = remote, inputKey = Keys.HDMI1,
            initial = denseWidgetButtons(), onSave = {}, onCancel = {},
        )
    }

    @Test fun language() = shoot("11-language.png") {
        LanguageScreen(AppLanguages.all, AppLanguages.GERMAN, onContinue = {}, onBack = {})
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp-port-xxhdpi")
    fun wizardEnglish() = shoot("12-wizard-en.png") {
        Wizard(WizardState.TestCode(samsung.id, index = 2, total = samsung.codeSetCount, codeSetId = 0, testKey = Keys.MUTE))
    }

    /** Aufgezogene Sperrbildschirm-Benachrichtigung: echte Layouts aus [QuickNotification], Rahmen nachgebildet. */
    @Test fun lockScreen() {
        QuickNotification(app).update(remote, QuickPrefs(Keys.HDMI1, lockScreen = true, lockStyle = LockStyle.EXPANDED, fullRemote = true))
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(QuickNotification.ID)
            ?: error("keine Benachrichtigung")
        val screen = backdrop(Color.parseColor("#1B2A44"), Color.parseColor("#07090D"))
        val column = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(72), dp(12), dp(24))
        }
        column.addView(text("21:30", 76f, Color.WHITE, bold = false).apply { gravity = Gravity.CENTER_HORIZONTAL }, wrap())
        column.addView(text("Dienstag, 24. November", 16f, Color.parseColor("#CCFFFFFF")).apply { gravity = Gravity.CENTER_HORIZONTAL }, wrap())
        val card = card().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val sub = n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        card.addView(text("${app.getString(R.string.app_name)} · $sub", 12f, Color.parseColor("#B3ECEEF0")), wrap())
        val big = (n.bigContentView ?: error("keine erweiterte Ansicht")).apply(app, card)
        card.addView(big, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(40) })
        screen.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        screen.addView(caption(), captionParams())
        save(render(screen), "13-lockscreen-mini-remote.png")
    }

    /** Zwei Widgets auf dem Startbildschirm: echte Layouts aus [QuickWidgetProvider], Rahmen nachgebildet. */
    @Test fun widgets() {
        RemoteStore(File(app.filesDir, "remotes.json")).add(remote)
        val settings = QuickControls.settings(app)
        settings.inputKey = Keys.HDMI1
        val manager = shadowOf(AppWidgetManager.getInstance(app))
        fun widget(buttons: List<QuickButton>): View {
            val id = manager.createWidget(QuickWidgetProvider::class.java, R.layout.widget_quick)
            settings.setWidgetButtons(id, buttons)
            QuickWidgetProvider.update(app, id)
            return manager.getViewFor(id).also { (it.parent as? ViewGroup)?.removeView(it) }
        }
        val standard = widget(listOf(QuickButton.Key(Keys.POWER), QuickButton.Key(Keys.VOL_DOWN), QuickButton.Key(Keys.VOL_UP), QuickButton.Input))
        val dense = widget(denseWidgetButtons())
        val screen = backdrop(Color.parseColor("#3A5E8C"), Color.parseColor("#1A2333"))
        val column = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(96), dp(16), dp(24))
        }
        column.addView(text("21:30", 64f, Color.WHITE).apply { gravity = Gravity.CENTER_HORIZONTAL }, wrap())
        column.addView(text("Dienstag, 24. November", 16f, Color.parseColor("#E6FFFFFF")).apply { gravity = Gravity.CENTER_HORIZONTAL }, wrap())
        for (w in listOf(standard, dense)) {
            column.addView(w, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88)).apply { topMargin = dp(40) })
        }
        screen.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        screen.addView(caption(), captionParams())
        save(render(screen), "14-widget.png")
    }

    /** Sechs Knöpfe: Stumm, HDMI 1/2, OK, CH ▲ und eine Automation. */
    private fun denseWidgetButtons(): List<QuickButton> = listOf(
        QuickButton.Key(Keys.MUTE), QuickButton.Key(Keys.HDMI1), QuickButton.Key(Keys.HDMI2), QuickButton.Key(Keys.OK),
        QuickButton.Key(Keys.CH_UP),
    ) + remote.automations.take(1).map { QuickButton.Run(it.id, it.name) }

    @Composable
    private fun Remote(recording: RecordingBarState? = null) = RemoteScreen(
        remote = remote, remotes = listOf(remote), irAvailable = true, handler = NoKeys,
        onSwitch = {}, onOpenList = {}, onMenu = {}, recording = recording,
    )

    @Composable
    private fun Wizard(state: WizardState) = WizardScreen(
        state = state, brands = brands, irAvailable = true, savedRemotes = emptyList(),
        onPickBrand = {}, onStartScan = {}, onTest = {}, onAnswer = {}, onTvOff = {},
        onStopScan = {}, onResumeScan = {}, onSave = { _, _ -> }, onBack = {}, onLanguage = {},
    )

    /** Rendert [content] in der Activity; Dialogfenster (eigene Fenster) werden mit Abdunklung darübergelegt. */
    private fun shoot(name: String, store: String? = null, scrollTo: String? = null, content: @Composable () -> Unit) {
        rule.setContent { HotelRemoteTheme(content) }
        rule.waitForIdle()
        scrollTo?.let { rule.onNodeWithTag(it).performScrollTo() }
        rule.waitForIdle()
        // captureToImage() wartet unter Robolectric vergeblich auf einen Frame; daher direkt zeichnen.
        val decor = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(bitmap)
            decor.draw(canvas)
            for ((view, params) in windows()) {
                if (view === decor || !view.isShown || view.width == 0) continue
                if (params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                    canvas.drawColor(Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0))
                }
                // Robolectric legt Dialogfenster oben an; wie auf dem Gerät senkrecht zentrieren.
                val at = IntArray(2).also(view::getLocationOnScreen)
                val top = if (at[1] == 0 && params.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) (bitmap.height - view.height) / 2 else at[1]
                canvas.save()
                canvas.translate(at[0].toFloat(), top.toFloat())
                view.draw(canvas)
                canvas.restore()
            }
        }
        assertEquals("Breite", 1080, bitmap.width)
        assertEquals("Höhe", 1920, bitmap.height)
        save(bitmap, name)
        if (store != null) File(storeDir, store).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Alle Fenster der App in Reihenfolge (Activity, dann Dialoge) aus WindowManagerGlobal. */
    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<Pair<View, WindowManager.LayoutParams>> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)!!
        fun <T> list(field: String) = global.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(global) as List<T>
        return list<View>("mViews").zip(list<WindowManager.LayoutParams>("mParams"))
    }

    private fun save(bitmap: Bitmap, name: String) =
        File(docsDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    // --- Rahmen für Sperrbildschirm und Startbildschirm (einfache Views, 360 x 640 dp) ---

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), app.resources.displayMetrics).toInt()

    private fun backdrop(top: Int, bottom: Int) = FrameLayout(app).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
    }

    private fun card() = LinearLayout(app).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#F02B2D31"))
            cornerRadius = dp(24).toFloat()
        }
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(app).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun caption() = text("Darstellung · Layout der App, Rahmen nachgebildet", 12f, Color.parseColor("#99FFFFFF")).apply {
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun captionParams() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
        bottomMargin = dp(20)
    }

    private fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun render(view: View): Bitmap {
        val width = app.resources.displayMetrics.widthPixels
        val height = app.resources.displayMetrics.heightPixels
        assertEquals("Breite", 1080, width)
        assertEquals("Höhe", 1920, height)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private object NoKeys : KeyHandler {
        override fun press(key: String) = Unit
        override fun hold(key: String): Job? = null
    }

    companion object {
        const val DIR_PROPERTY = "hotelremote.screenshots.dir"
    }
}
