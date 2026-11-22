package de.hotelremote.ui.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.Brand
import de.hotelremote.ui.remote.KeyBrush
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.theme.HotelRemoteTheme
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.wizard.WizardState

private val YesBrush = Brush.verticalGradient(listOf(RemoteColors.YesTop, RemoteColors.YesBottom))
private val FieldColor = Color(0xFF3A3C41)
private val RowColor = Color(0xFF36383D)

@Composable
fun WizardScreen(
    state: WizardState,
    brands: List<Brand>,
    onPickBrand: (Long) -> Unit,
    onStartScan: () -> Unit,
    onTest: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onTvOff: () -> Unit,
    onStopScan: () -> Unit,
    onResumeScan: (Int) -> Unit,
    onSave: (name: String?, brandName: String) -> Unit,
    onBack: () -> Unit,
) {
    // Hinweis vor dem Suchlauf: lokaler Zwischenschritt, kein Zustand des Automaten.
    var intro by rememberSaveable { mutableStateOf(false) }
    val showIntro = intro && (state is WizardState.PickBrand || state is WizardState.NoMatch)
    BackHandler {
        if (showIntro) intro = false else onBack()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (showIntro) {
            ScanIntro(onStart = {
                intro = false
                onStartScan()
            })
        } else {
            when (state) {
                WizardState.PickBrand -> PickBrand(brands, onPickBrand, onUnknown = { intro = true })
                is WizardState.TestCode -> TestCode(
                    state,
                    brandName = brands.firstOrNull { it.id == state.brandId }?.name.orEmpty(),
                    onTest = onTest, onAnswer = onAnswer, onTvOff = onTvOff,
                )
                is WizardState.Confirm -> Confirm(state, onTest, onAnswer)
                is WizardState.Scanning -> Scanning(state, onStopScan)
                is WizardState.ScanVerify -> ScanVerify(state, onTest, onAnswer)
                is WizardState.TestInputs -> TestInputs(state, onTest, onAnswer)
                is WizardState.Save -> Save(state, onSave)
                is WizardState.NoMatch -> NoMatch(
                    state,
                    onOtherBrand = onBack,
                    onStartScan = { intro = true },
                    onResume = onResumeScan,
                )
            }
        }
    }
}

// ---- Bausteine ----

@Composable
private fun Title(text: String) =
    Text(text, color = RemoteColors.KeyText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)

@Composable
private fun Sub(text: String, modifier: Modifier = Modifier) =
    Text(text, color = RemoteColors.KeyText.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 20.sp, modifier = modifier)

@Composable
private fun Question(text: String) =
    Text(
        text,
        color = RemoteColors.KeyText, fontSize = 16.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

@Composable
private fun Label(text: String) =
    Text(
        text,
        color = RemoteColors.KeyText.copy(alpha = 0.5f), fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
    )

@Composable
private fun WizardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = KeyBrush,
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoYes(onAnswer: (Boolean) -> Unit, noText: String = stringResource(R.string.wiz_no)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WizardButton(noText, { onAnswer(false) }, Modifier.weight(1f))
        WizardButton(stringResource(R.string.wiz_yes), { onAnswer(true) }, Modifier.weight(1f), brush = YesBrush)
    }
}

@Composable
private fun Header(left: String, progress: Float? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(left, color = RemoteColors.KeyText.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (progress != null) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(FieldColor)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(RemoteColors.SourceTop))
            }
        }
    }
}

/** Großer runder Testknopf in der Mitte des freien Platzes. */
@Composable
private fun ColumnScope.BigTestButton(label: String, onClick: () -> Unit, brush: Brush = KeyBrush) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(140.dp).clip(CircleShape).background(brush).clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun keyDisplay(key: String): String = when (key) {
    Keys.MUTE -> stringResource(R.string.wiz_key_mute)
    Keys.VOL_DOWN -> stringResource(R.string.wiz_key_vol_down)
    Keys.VOL_UP -> stringResource(R.string.wiz_key_vol_up)
    else -> stringResource(R.string.wiz_key_power)
}

private fun isPower(key: String) = key == Keys.POWER || key == Keys.POWER_ON

// ---- Bildschirme ----

@Composable
private fun PickBrand(brands: List<Brand>, onPick: (Long) -> Unit, onUnknown: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = brands.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val hotel = filtered.filter { it.hotelRank != null }.sortedBy { it.hotelRank }
    val all = filtered.sortedBy { it.name.lowercase() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title(stringResource(R.string.wiz_pick_title))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.wiz_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (hotel.isNotEmpty()) {
                item { Label(stringResource(R.string.wiz_section_hotel)) }
                items(hotel, key = { "h${it.id}" }) { BrandRow(it, onPick) }
            }
            if (all.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Label(stringResource(R.string.wiz_section_all))
                }
                items(all, key = { "a${it.id}" }) { BrandRow(it, onPick) }
            }
        }
        WizardButton(stringResource(R.string.wiz_unknown_brand), onUnknown, Modifier.fillMaxWidth(), brush = SourceBrush)
    }
}

@Composable
private fun BrandRow(brand: Brand, onPick: (Long) -> Unit) {
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
            stringResource(R.string.wiz_codes_count, brand.codeSetCount),
            color = RemoteColors.KeyText.copy(alpha = 0.45f),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TestCode(
    s: WizardState.TestCode,
    brandName: String,
    onTest: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onTvOff: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(
            stringResource(R.string.wiz_test_header, brandName, s.index + 1, s.total),
            progress = (s.index + 1).toFloat() / s.total,
        )
        Title(stringResource(R.string.wiz_test_title, keyDisplay(s.testKey)))
        BigTestButton(keyDisplay(s.testKey), onTest)
        Question(
            stringResource(
                when (s.testKey) {
                    Keys.MUTE -> R.string.wiz_q_mute
                    Keys.VOL_DOWN -> R.string.wiz_q_vol_down
                    else -> R.string.wiz_q_power
                },
            ),
        )
        NoYes(onAnswer, stringResource(R.string.wiz_no_next))
        if (!isPower(s.testKey)) {
            Text(
                stringResource(R.string.wiz_tv_off_link),
                color = Color(0xFF7FA6E8), fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onTvOff).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Confirm(s: WizardState.Confirm, onTest: () -> Unit, onAnswer: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(stringResource(R.string.wiz_confirm_title, keyDisplay(s.confirmKey)))
        BigTestButton(keyDisplay(s.confirmKey), onTest)
        Question(stringResource(R.string.wiz_confirm_question))
        NoYes(onAnswer)
    }
}

@Composable
private fun ScanIntro(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Title(stringResource(R.string.wiz_scan_intro))
        WizardButton(stringResource(R.string.wiz_scan_start), onStart, Modifier.fillMaxWidth(), brush = SourceBrush)
    }
}

@Composable
private fun Scanning(s: WizardState.Scanning, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(stringResource(R.string.wiz_scan_title))
        Sub(stringResource(R.string.wiz_scan_text))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(180.dp).border(BorderStroke(4.dp, RemoteColors.SourceTop), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.wiz_scan_progress, s.position + 1, s.total, s.brandLabel),
                    color = RemoteColors.KeyText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
                )
            }
        }
        WizardButton(stringResource(R.string.wiz_scan_stop), onStop, Modifier.fillMaxWidth().height(64.dp), brush = YesBrush)
    }
}

@Composable
private fun ScanVerify(s: WizardState.ScanVerify, onTest: () -> Unit, onAnswer: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(stringResource(R.string.wiz_verify_header, s.index + 1, s.candidates.size))
        Title(stringResource(R.string.wiz_verify_title))
        BigTestButton(stringResource(R.string.wiz_key_power), onTest)
        NoYes(onAnswer)
    }
}

@Composable
private fun TestInputs(s: WizardState.TestInputs, onTest: () -> Unit, onAnswer: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(stringResource(R.string.wiz_inputs_title))
        Sub(stringResource(R.string.wiz_inputs_text))
        BigTestButton(stringResource(R.string.wiz_hdmi1), onTest, brush = SourceBrush)
        NoYes(onAnswer)
        Sub(stringResource(R.string.wiz_inputs_footer, s.variantIndex + 1, s.variantCount))
    }
}

@Composable
private fun Save(s: WizardState.Save, onSave: (String?, String) -> Unit) {
    var brand by rememberSaveable(s.brandName) { mutableStateOf(s.brandName) }
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title(stringResource(R.string.wiz_save_title, s.brandName, s.codeLabel))
        CheckLine(stringResource(R.string.wiz_save_sound), true)
        CheckLine(stringResource(R.string.wiz_save_hdmi), s.hasInputs)
        Label(stringResource(R.string.wiz_field_brand))
        OutlinedTextField(brand, { brand = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Label(stringResource(R.string.wiz_field_name))
        OutlinedTextField(
            name, { name = it },
            placeholder = { Text(stringResource(R.string.wiz_name_hint)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        WizardButton(
            stringResource(R.string.wiz_to_remote),
            { onSave(name.ifBlank { null }, brand) },
            Modifier.fillMaxWidth(), brush = YesBrush,
        )
    }
}

@Composable
private fun CheckLine(text: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✔" else "✕",
            color = if (ok) Color(0xFF5FD37A) else Color(0xFFE0675F),
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
        Text(text, color = RemoteColors.KeyText, fontSize = 15.sp)
    }
}

@Composable
private fun NoMatch(
    s: WizardState.NoMatch,
    onOtherBrand: () -> Unit,
    onStartScan: () -> Unit,
    onResume: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Title(stringResource(R.string.wiz_nomatch_title))
        WizardButton(stringResource(R.string.wiz_other_brand), onOtherBrand, Modifier.fillMaxWidth())
        WizardButton(stringResource(R.string.wiz_scan_start), onStartScan, Modifier.fillMaxWidth(), brush = SourceBrush)
        s.canResumeScanAt?.let { at ->
            WizardButton(stringResource(R.string.wiz_scan_resume), { onResume(at) }, Modifier.fillMaxWidth())
        }
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun WizardPreview() {
    HotelRemoteTheme {
        WizardScreen(
            state = WizardState.TestCode(1, 0, 12, 10, Keys.MUTE),
            brands = listOf(Brand(1, "Samsung", 1, 12), Brand(2, "Acme", null, 3)),
            onPickBrand = {}, onStartScan = {}, onTest = {}, onAnswer = {}, onTvOff = {},
            onStopScan = {}, onResumeScan = {}, onSave = { _, _ -> }, onBack = {},
        )
    }
}
