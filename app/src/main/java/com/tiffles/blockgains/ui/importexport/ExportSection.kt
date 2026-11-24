package com.tiffles.blockgains.ui.importexport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.ui.components.AssetFilterBar
import com.tiffles.blockgains.ui.components.SectionHeader
import com.tiffles.blockgains.ui.components.WarningCard
import com.tiffles.blockgains.ui.components.YearPicker
import com.tiffles.blockgains.ui.format.DateFormatter
import java.time.ZoneId

data class ExportActions(
    val onYear: (Int) -> Unit = {},
    val onFilter: (AssetFilter) -> Unit = {},
    val onExport: (ExportKind, Uri) -> Unit = { _, _ -> },
    val onRestorePicked: (Uri) -> Unit = {},
    val onConfirmRestore: () -> Unit = {},
    val onDismissRestore: () -> Unit = {},
    val onReviewIssues: () -> Unit = {},
    val onImport: () -> Unit = {},
)

private data class ExportItem(val kind: ExportKind, val title: Int, val body: Int, val icon: ImageVector, val mime: String)

private val YEAR_EXPORTS = listOf(
    ExportItem(ExportKind.TAXABLE_EVENTS, R.string.export_events_title, R.string.export_events_body, Icons.Outlined.TableChart, "text/csv"),
    ExportItem(ExportKind.FORM_8949, R.string.export_8949_title, R.string.export_8949_body, Icons.Outlined.Receipt, "text/csv"),
    ExportItem(ExportKind.INCOME, R.string.export_income_title, R.string.export_income_body, Icons.Outlined.Savings, "text/csv"),
    ExportItem(ExportKind.PDF_REPORT, R.string.export_pdf_title, R.string.export_pdf_body, Icons.Outlined.PictureAsPdf, "application/pdf"),
)
private val ALL_TRANSACTIONS = ExportItem(ExportKind.ALL_TRANSACTIONS, R.string.export_all_title, R.string.export_all_body, Icons.Outlined.Description, "text/csv")
private val BACKUP = ExportItem(ExportKind.BACKUP, R.string.export_backup_title, R.string.export_backup_body, Icons.Outlined.Backup, "application/json")

@Composable
fun ExportSection(state: ExportUiState, actions: ExportActions, modifier: Modifier = Modifier) {
    if (!state.hasRecords) {
        NoRecordsExport(state, actions, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.export_year_scope), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            YearPicker(state.year, state.years, actions.onYear)
        }
        AssetFilterBar(state.filter, actions.onFilter)
        Text(stringResource(R.string.export_scope_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.shortfalls > 0) {
            WarningCard(
                title = pluralStringResource(R.plurals.export_shortfall_title, state.shortfalls, state.shortfalls, state.year),
                body = stringResource(R.string.export_shortfall_body),
                onReview = actions.onReviewIssues,
            )
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ExportGroup {
            YEAR_EXPORTS.forEach { ExportRow(it, state.fileName(it.kind), enabled = !state.busy, onExport = actions.onExport) }
        }
        SectionHeader(stringResource(R.string.export_records_header))
        ExportGroup {
            ExportRow(ALL_TRANSACTIONS, state.fileName(ExportKind.ALL_TRANSACTIONS), enabled = !state.busy, onExport = actions.onExport)
            ExportRow(BACKUP, state.fileName(ExportKind.BACKUP), enabled = !state.busy, onExport = actions.onExport)
            RestoreRow(enabled = !state.busy, onPicked = actions.onRestorePicked)
        }
    }
    state.restore?.let { RestoreDialog(it, actions.onConfirmRestore, actions.onDismissRestore) }
}

/**
 * Empty state: nothing to export yet, so no export rows; points to Import. Restore stays, because
 * restoring a backup is how an empty app gets its records back.
 */
@Composable
private fun NoRecordsExport(state: ExportUiState, actions: ExportActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.export_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.export_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = actions.onImport, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.action_import_transactions)) }
        }
        ExportGroup { RestoreRow(enabled = !state.busy, onPicked = actions.onRestorePicked) }
    }
    state.restore?.let { RestoreDialog(it, actions.onConfirmRestore, actions.onDismissRestore) }
}

@Composable
private fun ExportGroup(content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun ExportRow(item: ExportItem, fileName: String, enabled: Boolean, onExport: (ExportKind, Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(item.mime)) { uri -> uri?.let { onExport(item.kind, it) } }
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { launcher.launch(fileName) },
        leadingContent = { Icon(item.icon, contentDescription = null) },
        headlineContent = { Text(stringResource(item.title)) },
        supportingContent = { Text(stringResource(item.body)) },
    )
    HorizontalDivider()
}

@Composable
private fun RestoreRow(enabled: Boolean, onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { launcher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        leadingContent = { Icon(Icons.Outlined.Restore, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.restore_title)) },
        supportingContent = { Text(stringResource(R.string.restore_body)) },
    )
}

@Composable
private fun RestoreDialog(preview: RestorePreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val count = preview.backup.transactions.size
    val exported = preview.backup.exportedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restore_exported_on, DateFormatter.format(exported)))
                Text(pluralStringResource(R.plurals.restore_record_count, count, count))
                if (preview.firstDate != null && preview.lastDate != null) {
                    Text(stringResource(R.string.preview_date_range, DateFormatter.format(preview.firstDate), DateFormatter.format(preview.lastDate)))
                }
                Text(stringResource(R.string.restore_warning), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
