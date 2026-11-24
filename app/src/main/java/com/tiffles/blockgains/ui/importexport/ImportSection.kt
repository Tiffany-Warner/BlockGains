package com.tiffles.blockgains.ui.importexport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiffles.blockgains.R

/** MIME types CSV files commonly arrive with from different document providers. */
val CSV_MIME_TYPES = arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

data class ImportActions(
    val onPick: (Uri) -> Unit = {},
    val onPreview: () -> Unit = {},
    val onSaveTemplate: (Uri) -> Unit = {},
    val onOpenOpeningPositions: () -> Unit = {},
)

@Composable
fun ImportSection(state: ImportUiState, actions: ImportActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.unified_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilePickerRow(stringResource(R.string.unified_file), state.file, actions.onPick)
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = actions.onPreview, enabled = state.canPreview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.preview_import))
                }
            }
        }
        TemplateCard(actions.onSaveTemplate)
        OpeningPositionsCard(actions.onOpenOpeningPositions)
    }
}

@Composable
private fun FilePickerRow(label: String, file: PickedFile?, onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                file?.name ?: stringResource(R.string.no_file),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        OutlinedButton(onClick = { launcher.launch(CSV_MIME_TYPES) }) {
            Text(stringResource(if (file == null) R.string.choose_file else R.string.change_file))
        }
    }
}

@Composable
private fun TemplateCard(onSave: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(onSave) }
    InfoActionCard(
        title = stringResource(R.string.template_title),
        body = stringResource(R.string.template_body),
        action = stringResource(R.string.template_button),
        onAction = { launcher.launch(TEMPLATE_FILE_NAME) },
    )
}

@Composable
private fun OpeningPositionsCard(onOpen: () -> Unit) {
    InfoActionCard(
        title = stringResource(R.string.opening_card_title),
        body = stringResource(R.string.opening_card_body),
        action = stringResource(R.string.opening_card_button),
        onAction = onOpen,
    )
}

@Composable
fun InfoActionCard(title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

private const val TEMPLATE_FILE_NAME = "blockgains-template.csv"
