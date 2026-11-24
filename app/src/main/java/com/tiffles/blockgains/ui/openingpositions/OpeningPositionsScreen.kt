package com.tiffles.blockgains.ui.openingpositions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.Lot
import com.tiffles.blockgains.domain.usecase.DraftProblem
import com.tiffles.blockgains.domain.usecase.OpeningLotDraft
import com.tiffles.blockgains.ui.components.BackButton
import com.tiffles.blockgains.ui.components.EmptyState
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.components.MarkerChip
import com.tiffles.blockgains.ui.components.WalletField
import com.tiffles.blockgains.ui.components.issueText
import com.tiffles.blockgains.ui.format.DateFormatter
import com.tiffles.blockgains.ui.format.MoneyFormatter
import com.tiffles.blockgains.ui.format.QuantityFormatter
import com.tiffles.blockgains.ui.summary.InfoNote
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import java.math.BigDecimal
import java.time.LocalDate

data class OpeningActions(
    val onWallet: (asset: String, lot: Int, row: Int, wallet: String) -> Unit = { _, _, _, _ -> },
    val onQuantity: (asset: String, lot: Int, row: Int, text: String) -> Unit = { _, _, _, _ -> },
    val onSplit: (asset: String, lot: Int) -> Unit = { _, _ -> },
    val onRemove: (asset: String, lot: Int, row: Int) -> Unit = { _, _, _ -> },
    val onSave: (asset: String) -> Unit = {},
    val onRemoveOrphans: (asset: String) -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun OpeningPositionsScreen(onBack: () -> Unit, viewModel: OpeningPositionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OpeningPositionsContent(
        state,
        OpeningActions(
            onWallet = viewModel::setWallet,
            onQuantity = viewModel::setQuantity,
            onSplit = viewModel::addSplit,
            onRemove = viewModel::removeSplit,
            onSave = viewModel::save,
            onRemoveOrphans = viewModel::removeOrphans,
            onBack = onBack,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpeningPositionsContent(state: OpeningPositionsUiState, actions: OpeningActions) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.opening_title)) }, navigationIcon = { BackButton(actions.onBack) }) },
    ) { padding ->
        when (state) {
            OpeningPositionsUiState.Loading -> LoadingState(Modifier.padding(padding))
            is OpeningPositionsUiState.Content -> if (state.assets.isEmpty() && state.orphans.isEmpty()) {
                EmptyState(
                    Icons.Outlined.Inventory2,
                    stringResource(R.string.opening_empty_title),
                    stringResource(R.string.opening_empty_body),
                    modifier = Modifier.padding(padding),
                )
            } else {
                OpeningList(state, actions, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun OpeningList(state: OpeningPositionsUiState.Content, actions: OpeningActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.opening_intro), style = MaterialTheme.typography.bodyMedium) }
        state.assets.forEach { asset ->
            item(key = "header-${asset.asset}") { AssetHeader(asset) }
            itemsIndexed(asset.drafts, key = { index, _ -> "${asset.asset}-$index" }) { index, draft ->
                LotCard(asset.asset, index, draft, asset.inputs[index], state.wallets, actions)
            }
            item(key = "save-${asset.asset}") {
                Button(onClick = { actions.onSave(asset.asset) }, enabled = asset.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.opening_save, asset.asset))
                }
            }
        }
        state.orphans.forEach { orphan ->
            item(key = "orphan-${orphan.asset}") { OrphanCard(orphan, actions) }
        }
    }
}

@Composable
private fun AssetHeader(asset: OpeningAsset) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(asset.asset, style = MaterialTheme.typography.titleLarge)
        val saved = asset.savedTotal
        Text(
            if (saved != null) stringResource(R.string.opening_saved_records, qty(saved, asset.asset)) else stringResource(R.string.opening_not_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        asset.mismatch?.let { InfoNote(issueText(it).body) }
    }
}

@Composable
private fun LotCard(
    asset: String,
    lotIndex: Int,
    draft: OpeningLotDraft,
    inputs: List<AllocationInput>,
    wallets: List<String>,
    actions: OpeningActions,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.opening_lot_title, DateFormatter.format(draft.lot.acquiredDate)), style = MaterialTheme.typography.titleSmall)
                if (draft.lot.basisUnknown) MarkerChip(stringResource(R.string.basis_unknown_marker))
            }
            Text(
                pluralStringResource(
                    R.plurals.opening_lot_body,
                    QuantityFormatter.pluralCount(draft.lot.quantity),
                    QuantityFormatter.full(draft.lot.quantity),
                    MoneyFormatter.format(draft.lot.basis),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            inputs.forEachIndexed { row, input ->
                AllocationRow(input, wallets, removable = inputs.size > 1, asset, lotIndex, row, actions)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                problemText(draft)?.let { Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    ?: androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = { actions.onSplit(asset, lotIndex) }) {
                    Icon(Icons.Outlined.CallSplit, contentDescription = null)
                    Text(stringResource(R.string.opening_split), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun AllocationRow(
    input: AllocationInput,
    wallets: List<String>,
    removable: Boolean,
    asset: String,
    lot: Int,
    row: Int,
    actions: OpeningActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WalletField(input.wallet, wallets, { actions.onWallet(asset, lot, row, it) }, showHint = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input.quantityText,
                onValueChange = { actions.onQuantity(asset, lot, row, it) },
                label = { Text(stringResource(R.string.label_quantity)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            if (removable) {
                IconButton(onClick = { actions.onRemove(asset, lot, row) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.opening_remove_cd))
                }
            }
        }
    }
}

@Composable
private fun problemText(draft: OpeningLotDraft): String? = when (draft.problem) {
    null -> null
    DraftProblem.BLANK_WALLET -> stringResource(R.string.opening_blank_wallet)
    DraftProblem.NON_POSITIVE -> stringResource(R.string.opening_non_positive)
    DraftProblem.UNASSIGNED -> stringResource(R.string.opening_unassigned, QuantityFormatter.full(draft.unassigned))
    DraftProblem.OVER_ALLOCATED -> stringResource(R.string.opening_over, QuantityFormatter.full(draft.unassigned.negate()))
}

@Composable
private fun OrphanCard(orphan: OrphanOpenings, actions: OpeningActions) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.opening_orphans_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.opening_orphans_body, orphan.asset, qty(orphan.total, orphan.asset)), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { actions.onRemoveOrphans(orphan.asset) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.opening_remove_saved))
            }
        }
    }
}

@Composable
private fun qty(value: BigDecimal, asset: String) = stringResource(R.string.quantity_asset, QuantityFormatter.full(value), asset)

@PreviewLightDark
@Composable
private fun OpeningPreview() {
    val lots = listOf(
        Lot(1, "FAKEB", AssetClass.CRYPTO, "FAKE-WALLET-1", LocalDate.of(2021, 9, 4), BigDecimal("3.521"), BigDecimal("497.48"), false),
        Lot(2, "FAKEB", AssetClass.CRYPTO, "FAKE-WALLET-1", LocalDate.of(2023, 2, 10), BigDecimal("1.5"), BigDecimal.ZERO, true),
    )
    val asset = OpeningAsset(
        asset = "FAKEB",
        lots = lots,
        inputs = listOf(
            listOf(AllocationInput("FAKE-WALLET-1", "2"), AllocationInput("FAKE-EXCHANGE-1", "1.5")),
            listOf(AllocationInput("FAKE-WALLET-1", "1.5")),
        ),
        savedTotal = null,
        mismatch = null,
        edited = true,
    )
    BlockGainsTheme(dynamicColor = false) {
        OpeningPositionsContent(
            OpeningPositionsUiState.Content(listOf(asset), listOf(OrphanOpenings("FAKEC", BigDecimal("12"))), listOf("FAKE-EXCHANGE-1", "FAKE-WALLET-1"), "FAKE-WALLET-1"),
            OpeningActions(),
        )
    }
}
