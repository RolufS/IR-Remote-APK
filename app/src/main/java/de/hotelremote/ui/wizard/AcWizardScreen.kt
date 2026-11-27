package de.hotelremote.ui.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.data.AcBrand
import de.hotelremote.data.acTempText
import de.hotelremote.ui.DeviceKind
import de.hotelremote.ui.ac.fanName
import de.hotelremote.ui.ac.label
import de.hotelremote.ui.ac.modeName
import de.hotelremote.ui.remote.NoIrBanner
import de.hotelremote.ui.remote.PowerIcon
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.wizard.AcWizardState

/** Hintergrund und Ränder wie im TV-Assistenten. */
@Composable
private fun WizardFrame(irAvailable: Boolean = true, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!irAvailable) NoIrBanner()
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

/** Start eines neuen Raums: Fernseher oder Klimaanlage einrichten. */
@Composable
fun DeviceChoiceScreen(onChoose: (DeviceKind) -> Unit, onBack: () -> Unit, onLanguage: (() -> Unit)? = null) {
    BackHandler(onBack = onBack)
    WizardFrame {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Title(stringResource(R.string.wiz_device_title))
            WizardButton(
                stringResource(R.string.wiz_device_tv), { onChoose(DeviceKind.TV) },
                Modifier.fillMaxWidth().height(72.dp).testTag("choose_tv"),
            )
            WizardButton(
                stringResource(R.string.wiz_device_ac), { onChoose(DeviceKind.AC) },
                Modifier.fillMaxWidth().height(72.dp).testTag("choose_ac"), brush = SourceBrush,
            )
            if (onLanguage != null) LanguageLink(onLanguage)
        }
    }
}

@Composable
private fun LanguageLink(onLanguage: () -> Unit) = Text(
    stringResource(R.string.menu_language),
    color = LinkColor, fontSize = 14.sp, textAlign = TextAlign.Center,
    modifier = Modifier
        .fillMaxWidth()
        .testTag("wizard_language")
        .clickable(role = Role.Button, onClick = onLanguage)
        .padding(vertical = 4.dp),
)

/**
 * Klima-Assistent: Marke wählen, Code-Test je Gerät, „Aus“ zur Bestätigung, Speichern. [addingTo]: Titel des
 * Raums, zu dem die Klimaanlage hinzugefügt wird (dann kein Namensfeld); null = neuer Raum.
 */
@Composable
fun AcWizardScreen(
    state: AcWizardState,
    brands: List<AcBrand>,
    irAvailable: Boolean,
    onPickBrand: (Long?) -> Unit,
    onTest: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onSave: (name: String?) -> Unit,
    onBack: () -> Unit,
    addingTo: String? = null,
) {
    BackHandler(onBack = onBack)
    WizardFrame(irAvailable) {
        when (state) {
            AcWizardState.PickBrand -> PickAcBrand(brands, onPickBrand)
            is AcWizardState.TestCode -> AcTestCode(state, onTest, onAnswer)
            is AcWizardState.ConfirmOff -> AcConfirmOff(state, onTest, onAnswer)
            is AcWizardState.Save -> AcSave(state, addingTo, onSave)
            is AcWizardState.NoMatch -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Title(stringResource(R.string.acwiz_nomatch_title))
                WizardButton(stringResource(R.string.wiz_other_brand), onBack, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PickAcBrand(brands: List<AcBrand>, onPick: (Long?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = brands.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val common = filtered.filter { it.commonRank != null }.sortedBy { it.commonRank }
    val all = filtered.sortedBy { it.name.lowercase() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title(stringResource(R.string.acwiz_pick_title))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.wiz_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ac_brand_search"),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (common.isNotEmpty()) {
                item { Label(stringResource(R.string.acwiz_section_common)) }
                items(common, key = { "c${it.id}" }) { AcBrandRow(it, onPick) }
            }
            if (all.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Label(stringResource(R.string.wiz_section_all))
                }
                items(all, key = { "a${it.id}" }) { AcBrandRow(it, onPick) }
            }
        }
        WizardButton(
            stringResource(R.string.acwiz_unknown_brand), { onPick(null) },
            Modifier.fillMaxWidth().testTag("ac_unknown_brand"), brush = SourceBrush,
        )
    }
}

@Composable
private fun AcBrandRow(brand: AcBrand, onPick: (Long?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RowColor)
            .clickable { onPick(brand.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(brand.name, color = RemoteColors.KeyText, fontSize = 16.sp)
        Text(
            stringResource(R.string.acwiz_devices_count, brand.deviceCount),
            color = RemoteColors.KeyText.copy(alpha = 0.45f),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AcTestCode(s: AcWizardState.TestCode, onTest: () -> Unit, onAnswer: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(
            stringResource(R.string.wiz_test_header, s.brandName, s.index + 1, s.total),
            progress = (s.index + 1).toFloat() / s.total,
        )
        Title(stringResource(R.string.acwiz_test_title))
        Sub(
            stringResource(
                R.string.acwiz_test_state,
                label(s.testState.mode, ::modeName),
                acTempText(s.testState.temp, s.fahrenheit),
                label(s.testState.fan, ::fanName),
            ),
            Modifier.testTag("ac_test_state"),
        )
        AcSendButton("test${s.deviceId}", onTest)
        Question(stringResource(R.string.acwiz_q_reacted))
        NoYes(onAnswer, stringResource(R.string.wiz_no_next))
    }
}

@Composable
private fun AcConfirmOff(s: AcWizardState.ConfirmOff, onTest: () -> Unit, onAnswer: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(stringResource(R.string.acwiz_off_title))
        AcSendButton("off${s.deviceId}", onTest)
        Question(stringResource(R.string.acwiz_off_question))
        NoYes(onAnswer, stringResource(R.string.wiz_no_next))
    }
}

/** Großer Sendeknopf in der Mitte; nach dem ersten Senden darunter „Nochmal senden“ (je [step] neu). */
@Composable
private fun ColumnScope.AcSendButton(step: String, onTest: () -> Unit) {
    var sent by rememberSaveable(step) { mutableStateOf(false) }
    val send = {
        sent = true
        onTest()
    }
    BigTestButton(stringResource(R.string.acwiz_send), send, icon = PowerIcon)
    if (sent) {
        Text(
            stringResource(R.string.acwiz_send_again),
            color = LinkColor, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ac_send_again")
                .clickable(role = Role.Button, onClick = send)
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun AcSave(s: AcWizardState.Save, addingTo: String?, onSave: (String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title(stringResource(R.string.wiz_save_title, s.ac.brand, s.ac.deviceLabel))
        if (addingTo != null) AddingTo(addingTo) else NameField(name) { name = it }
        Spacer(Modifier.height(4.dp))
        WizardButton(
            stringResource(R.string.wiz_to_remote),
            { onSave(name.trim().ifBlank { null }.takeIf { addingTo == null }) },
            Modifier.fillMaxWidth().testTag("ac_save"), brush = YesBrush,
        )
    }
}
