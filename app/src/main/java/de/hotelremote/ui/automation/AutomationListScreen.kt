package de.hotelremote.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.automation.AutomationProgress
import de.hotelremote.automation.AutomationTemplate
import de.hotelremote.automation.TemplateSections
import de.hotelremote.data.Automation
import de.hotelremote.ui.remote.PowerBrush
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.sleeptimer.Hint
import de.hotelremote.ui.theme.RemoteColors

/**
 * Automationen der aktuellen Fernbedienung: Tippen bearbeitet, ▶ führt aus, × löscht nach Rückfrage; neu anlegen oder
 * aufnehmen. Darunter Sleep-Timer-Vorlagen: passende zuerst, andere Marken aufklappbar; Tippen öffnet den Editor.
 */
@Composable
fun AutomationListScreen(
    automations: List<Automation>,
    progress: AutomationProgress?,
    onRun: (Automation) -> Unit,
    onCancelRun: () -> Unit,
    onEdit: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    onRecord: () -> Unit = {},
    templates: TemplateSections = TemplateSections(emptyList(), emptyList()),
    onTemplate: (AutomationTemplate) -> Unit = {},
) {
    var deleteFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showOthers by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(stringResource(R.string.auto_title), "automations_back", onBack)
        if (progress != null) AutomationProgressBar(progress, onCancelRun)
        LazyColumn(Modifier.weight(1f).testTag("automation_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (automations.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.auto_empty),
                        color = RemoteColors.KeyText.copy(alpha = 0.7f),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(automations, key = { it.id }) { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RowColor)
                        .testTag("automation_${a.id}")
                        .clickable(role = Role.Button) { onEdit(a.id) }
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, color = RemoteColors.KeyText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.auto_steps_count, a.steps.size),
                            color = RemoteColors.KeyText.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                        )
                    }
                    SymbolButton("▶", stringResource(R.string.cd_auto_run), "automation_run_${a.id}") { onRun(a) }
                    SymbolButton("×", stringResource(R.string.list_delete), "automation_delete_${a.id}") { deleteFor = a.id }
                }
            }
            templateItems(templates, showOthers, { showOthers = !showOthers }, onTemplate)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WideButton(stringResource(R.string.auto_new), "automation_new", SourceBrush, Modifier.weight(1.3f)) { onEdit(null) }
            WideButton(stringResource(R.string.rec_start), "automation_record", PowerBrush, Modifier.weight(1f), onClick = onRecord)
        }
    }

    automations.firstOrNull { it.id == deleteFor }?.let { a ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            text = { Text(stringResource(R.string.auto_delete_confirm, a.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteFor = null
                        onDelete(a.id)
                    },
                    modifier = Modifier.testTag("automation_delete_confirm"),
                ) { Text(stringResource(R.string.list_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.list_cancel)) }
            },
        )
    }
}

/** Abschnitt „Vorlagen“: passende Vorlagen, darunter aufklappbar die anderer Marken. */
private fun LazyListScope.templateItems(
    templates: TemplateSections,
    showOthers: Boolean,
    onToggleOthers: () -> Unit,
    onTemplate: (AutomationTemplate) -> Unit,
) {
    item {
        Text(
            stringResource(R.string.tpl_section),
            color = RemoteColors.KeyText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp).testTag("templates_header"),
        )
    }
    item { Hint(stringResource(R.string.tpl_section_hint), "templates_hint") }
    if (templates.matching.isEmpty()) {
        item {
            Text(
                stringResource(R.string.tpl_none),
                color = RemoteColors.KeyText.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(8.dp).testTag("templates_none"),
            )
        }
    }
    items(templates.matching, key = { "tpl_${it.id}" }) { TemplateRow(it, onTemplate) }
    if (templates.others.isNotEmpty()) {
        item {
            Text(
                (if (showOthers) "▾  " else "▸  ") + stringResource(R.string.tpl_more),
                color = RemoteColors.KeyText.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .testTag("templates_more")
                    .clickable(role = Role.Button, onClick = onToggleOthers)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
        if (showOthers) items(templates.others, key = { "tpl_${it.id}" }) { TemplateRow(it, onTemplate) }
    }
}

@Composable
private fun TemplateRow(t: AutomationTemplate, onTemplate: (AutomationTemplate) -> Unit) {
    val res = LocalContext.current.resources
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RowColor)
            .testTag("template_${t.id}")
            .clickable(role = Role.Button) { onTemplate(t) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(templateTitle(res, t), color = RemoteColors.KeyText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(templateSubtitle(res, t), color = RemoteColors.KeyText.copy(alpha = 0.55f), fontSize = 13.sp)
    }
}
