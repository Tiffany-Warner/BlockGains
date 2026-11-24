package com.tiffles.blockgains.ui.importexport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.ImportedRow
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.usecase.ImportPreview
import com.tiffles.blockgains.ui.components.BackButton
import com.tiffles.blockgains.ui.components.EmptyState
import com.tiffles.blockgains.ui.components.SectionHeader
import com.tiffles.blockgains.ui.components.invalidLocation
import com.tiffles.blockgains.ui.components.invalidReasonText
import com.tiffles.blockgains.ui.format.DateFormatter
import com.tiffles.blockgains.ui.format.QuantityFormatter
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import com.tiffles.blockgains.ui.theme.tabular
import java.math.BigDecimal
import java.time.LocalDate

data class PreviewActions(
    val onIncome: (Int, Boolean) -> Unit = { _, _ -> },
    val onFmv: (Int, String) -> Unit = { _, _ -> },
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

@Composable
fun ImportPreviewScreen(onDone: () -> Unit, viewModel: ImportPreviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    BackHandler(onBack = viewModel::cancel)
    ImportPreviewContent(state, PreviewActions(viewModel::setIncome, viewModel::setFmv, viewModel::confirm, viewModel::cancel))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewContent(state: ImportPreviewUiState, actions: PreviewActions) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.preview_title)) }, navigationIcon = { BackButton(actions.onCancel) }) },
        bottomBar = {
            if (state is ImportPreviewUiState.Content && !state.preview.isEmpty) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = actions.onConfirm,
                        enabled = state.canConfirm,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) { Text(pluralStringResource(R.plurals.preview_confirm, state.preview.toAdd.size, state.preview.toAdd.size)) }
                }
            }
        },
    ) { padding ->
        when (state) {
            ImportPreviewUiState.Expired -> EmptyState(Icons.Outlined.Inbox, stringResource(R.string.preview_expired), body = null, modifier = Modifier.padding(padding))
            is ImportPreviewUiState.Content -> PreviewBody(state, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PreviewBody(state: ImportPreviewUiState.Content, actions: PreviewActions, modifier: Modifier = Modifier) {
    val preview = state.preview
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Counts(preview) }
        if (preview.scientificValues > 0) {
            item {
                // Non-blocking: the values parse exactly, but a spreadsheet may already have rounded them.
                Text(
                    pluralStringResource(R.plurals.preview_scientific_note, preview.scientificValues, preview.scientificValues),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (preview.isEmpty) item { Text(stringResource(R.string.preview_nothing), style = MaterialTheme.typography.bodyLarge) }
        if (preview.zeroPriceIndices.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.preview_zero_title))
                Text(stringResource(R.string.preview_zero_body), style = MaterialTheme.typography.bodyMedium)
            }
            items(preview.zeroPriceIndices) { index ->
                ZeroPriceCard(preview.toAdd[index], state.drafts[index] ?: ZeroPriceDraft(), index, actions)
            }
        }
        if (preview.invalidRows.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.preview_problems))
                Text(stringResource(R.string.preview_problems_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(preview.invalidRows) { ProblemRow(it) }
        }
    }
}

@Composable
private fun Counts(preview: ImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile(stringResource(R.string.preview_to_add), preview.toAdd.size)
            CountTile(pluralStringResource(R.plurals.preview_duplicates_tile, preview.duplicates.size), preview.duplicates.size)
            CountTile(pluralStringResource(R.plurals.preview_problems_tile, preview.invalidRows.size), preview.invalidRows.size)
        }
        if (preview.firstDate != null && preview.lastDate != null) {
            Text(
                stringResource(R.string.preview_date_range, DateFormatter.format(preview.firstDate), DateFormatter.format(preview.lastDate)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountTile(label: String, count: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.widthIn(min = 104.dp)) {
        Column(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall.tabular())
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ZeroPriceCard(row: ImportedRow, draft: ZeroPriceDraft, index: Int, actions: PreviewActions) {
    val tx = row.transaction
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).selectableGroup()) {
            Text(
                "${DateFormatter.format(tx.date)} · " + stringResource(R.string.quantity_asset, QuantityFormatter.format(tx.quantity), tx.asset),
                style = MaterialTheme.typography.titleSmall,
            )
            ChoiceRow(stringResource(R.string.zero_unknown_basis), selected = !draft.asIncome) { actions.onIncome(index, false) }
            ChoiceRow(stringResource(R.string.zero_income), selected = draft.asIncome) { actions.onIncome(index, true) }
            if (draft.asIncome) {
                OutlinedTextField(
                    value = draft.fmvText,
                    onValueChange = { actions.onFmv(index, it) },
                    label = { Text(stringResource(R.string.zero_fmv)) },
                    isError = draft.showError,
                    supportingText = { if (draft.showError) Text(stringResource(R.string.zero_fmv_error)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProblemRow(issue: CalcIssue.InvalidRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}) {
        invalidLocation(issue)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        Text(invalidReasonText(issue.reason, issue.detail), style = MaterialTheme.typography.bodyMedium)
    }
}

@PreviewLightDark
@Composable
private fun PreviewScreenPreview() {
    fun row(line: Int, qty: String, price: String) = ImportedRow(
        Transaction(type = TransactionType.BUY, date = LocalDate.of(2023, 2, line), asset = "FAKEB", wallet = "FAKE-WALLET-1", quantity = BigDecimal(qty), priceUsd = BigDecimal(price)),
        line, "fakeb-buys.csv",
    )
    val preview = ImportPreview(
        toAdd = listOf(row(2, "1.25", "1500"), row(3, "0.5", "0"), row(4, "2.123456789", "1890.1")),
        duplicates = listOf(row(5, "1", "1000")),
        invalidRows = listOf(CalcIssue.InvalidRow(InvalidReason.BAD_DATE, lineNumber = 7, fileName = "fakeb-buys.csv", detail = "Date bought")),
        zeroPriceIndices = listOf(1),
        firstDate = LocalDate.of(2023, 2, 2),
        lastDate = LocalDate.of(2023, 2, 4),
        scientificValues = 2,
    )
    BlockGainsTheme(dynamicColor = false) {
        ImportPreviewContent(ImportPreviewUiState.Content(preview, mapOf(1 to ZeroPriceDraft(asIncome = true, fmvText = "")), saving = false), PreviewActions())
    }
}
