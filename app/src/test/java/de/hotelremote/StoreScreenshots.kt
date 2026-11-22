package de.hotelremote

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.data.Brand
import de.hotelremote.data.CodeDatabase
import de.hotelremote.data.CodeRepository
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.remote.KeyHandler
import de.hotelremote.ui.remote.RemoteScreen
import de.hotelremote.ui.theme.HotelRemoteTheme
import de.hotelremote.ui.wizard.WizardScreen
import de.hotelremote.wizard.WizardState
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Rendert die Play-Store-Screenshots (1080 x 1920) aus den echten Compose-Bildschirmen nach
 * `docs/play-store/`. Läuft nur mit `-Pscreenshots` (setzt [DIR_PROPERTY]), sonst übersprungen:
 * `./gradlew :app:testDebugUnitTest --tests de.hotelremote.StoreScreenshots -Pscreenshots`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w360dp-h640dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshots {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var outDir: File
    private lateinit var brands: List<Brand>

    @Before fun setUp() {
        val dir = System.getProperty(DIR_PROPERTY)
        assumeTrue("Nur mit -Pscreenshots", dir != null)
        outDir = File(dir!!).apply { mkdirs() }
        // Echte Marken und Code-Zahlen aus der mitgelieferten Datenbank.
        brands = CodeDatabase.open(ApplicationProvider.getApplicationContext()).use { CodeRepository(it).brands() }
    }

    private val samsung get() = brands.first { it.name == "Samsung" }

    @Test fun remote() = shoot("screenshot-1-remote.png") {
        val signal = IrSignal(freq = 38000, frame = listOf(4500, 4500, 560), periodMs = 108)
        val keys = listOf(
            Keys.POWER, Keys.MUTE, Keys.VOL_UP, Keys.VOL_DOWN, Keys.CH_UP, Keys.CH_DOWN,
            Keys.SOURCE, Keys.MENU, Keys.HOME, Keys.OK, Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT,
            Keys.BACK, Keys.EXIT, Keys.INFO, Keys.LIST, Keys.PRE_CH,
            Keys.RED, Keys.GREEN, Keys.YELLOW, Keys.BLUE,
        ) + Keys.HDMI + (0..9).map { "DIGIT_$it" } + listOf("X:Tools", "X:Guide")
        val remote = SavedRemote(
            id = "r1", name = null, brand = "Samsung", codeLabel = "Code 1",
            keys = keys.associateWith { signal }, lastUsed = 0L,
        )
        RemoteScreen(
            remote = remote, remotes = listOf(remote), irAvailable = true, handler = NoKeys,
            onSwitch = {}, onOpenList = {}, onMenu = {},
        )
    }

    @Test fun wizardBrand() = shoot("screenshot-2-wizard-brand.png") {
        Wizard(WizardState.PickBrand)
    }

    @Test fun wizardTest() = shoot("screenshot-3-wizard-test.png") {
        Wizard(WizardState.TestCode(samsung.id, index = 2, total = samsung.codeSetCount, codeSetId = 0, testKey = Keys.MUTE))
    }

    @Test fun inputs() = shoot("screenshot-4-inputs.png") {
        Wizard(WizardState.TestInputs(samsung.id, codeSetId = 0, variantIndex = 0, variantCount = 2))
    }

    @Composable
    private fun Wizard(state: WizardState) = WizardScreen(
        state = state, brands = brands, irAvailable = true, savedRemotes = emptyList(),
        onPickBrand = {}, onStartScan = {}, onTest = {}, onAnswer = {}, onTvOff = {},
        onStopScan = {}, onResumeScan = {}, onSave = { _, _ -> }, onBack = {},
    )

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { HotelRemoteTheme(content) }
        rule.waitForIdle()
        // captureToImage() wartet unter Robolectric vergeblich auf einen Frame; daher direkt zeichnen.
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        assertEquals("Breite", 1080, bitmap.width)
        assertEquals("Höhe", 1920, bitmap.height)
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private object NoKeys : KeyHandler {
        override fun press(key: String) = Unit
        override fun hold(key: String): Job? = null
    }

    companion object {
        const val DIR_PROPERTY = "hotelremote.screenshots.dir"
    }
}
