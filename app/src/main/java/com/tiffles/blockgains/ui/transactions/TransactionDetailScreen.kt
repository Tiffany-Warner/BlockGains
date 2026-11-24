package com.tiffles.blockgains.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.ui.components.BackButton
import com.tiffles.blockgains.ui.components.EmptyState
import com.tiffles.blockgains.ui.components.LabeledText
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.components.label
import com.tiffles.blockgains.ui.format.DateFormatter
import com.tiffles.blockgains.ui.format.MoneyFormatter
import com.tiffles.blockgains.ui.format.QuantityFormatter
import com.tiffles.blockgains.ui.navigation.Route
import com.tiffles.blockgains.ui.preview.PreviewData
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface TransactionDetailUiState {
    data object Loading : TransactionDetailUiState
    data object NotFound : TransactionDetailUiState
    /** [hasFeeEvent]: the row's network fee produced a taxable event (R13), which opens by the row's id. */
    data class Content(val transaction: Transaction, val hasFeeEvent: Boolean = false) : TransactionDetailUiState
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, ledgerSource: LedgerSource) : ViewModel() {
    private val id = savedStateHandle.toRoute<Route.TransactionDetail>().transactionId

    val state: StateFlow<TransactionDetailUiState> = ledgerSource.ledger.map { ledger ->
        when (val tx = ledger?.transactions?.find { it.id == id }) {
            null -> if (ledger == null) TransactionDetailUiState.Loading else TransactionDetailUiState.NotFound
            else -> TransactionDetailUiState.Content(tx, hasFeeEvent = ledger.result.taxableEvents.any { it.transactionId == id && it.isTransferFee })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailUiState.Loading)
}

@Composable
fun TransactionDetailScreen(onBack: () -> Unit, onOpenEvent: (Long) -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TransactionDetailContent(state, onBack, onOpenEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailContent(state: TransactionDetailUiState, onBack: () -> Unit, onOpenEvent: (Long) -> Unit = {}) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.transaction_detail_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        when (state) {
            TransactionDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            TransactionDetailUiState.NotFound -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.detail_not_found),
                body = null,
                modifier = Modifier.padding(padding),
            )
            is TransactionDetailUiState.Content -> TransactionFields(
                tx = state.transaction,
                onOpenFeeEvent = { onOpenEvent(state.transaction.id) }.takeIf { state.hasFeeEvent },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** Every stored column, full precision (ui-guidelines → Numbers: full precision in detail view). */
@Composable
private fun TransactionFields(tx: Transaction, onOpenFeeEvent: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("${stringResource(tx.type.label())} · ${tx.asset}", style = MaterialTheme.typography.headlineSmall)
        LabeledText(stringResource(R.string.label_date), DateFormatter.format(tx.date))
        LabeledText(stringResource(R.string.label_asset_class), stringResource(if (tx.assetClass == AssetClass.STOCK) R.string.filter_stocks else R.string.filter_crypto))
        LabeledText(stringResource(R.string.label_wallet), tx.wallet)
        val quantityLabel = if (tx.type == TransactionType.TRANSFER) R.string.label_amount_received else R.string.label_quantity
        LabeledText(stringResource(quantityLabel), QuantityFormatter.full(tx.quantity))
        tx.priceUsd?.let { LabeledText(stringResource(R.string.label_price), MoneyFormatter.format(it)) }
        tx.usdValue?.let { LabeledText(stringResource(R.string.label_total), MoneyFormatter.format(it)) }
        if (tx.networkFeeAsset != null) {
            NetworkFeeFields(tx, onOpenFeeEvent)
        } else {
            tx.feeUsd?.let { LabeledText(stringResource(R.string.label_fees), MoneyFormatter.format(it)) }
        }
        tx.toAsset?.let { asset ->
            val qty = tx.toQuantity?.let(QuantityFormatter::full).orEmpty()
            LabeledText(stringResource(R.string.label_received), stringResource(R.string.quantity_asset, qty, asset))
        }
        tx.toWallet?.let { LabeledText(stringResource(R.string.label_to_wallet), it) }
        tx.acquiredDate?.let { LabeledText(stringResource(R.string.label_acquired), DateFormatter.format(it)) }
        if (tx.basisUnknown) LabeledText(stringResource(R.string.basis_unknown_marker), stringResource(R.string.yes))
        tx.note?.let { LabeledText(stringResource(R.string.label_note), it) }
    }
}

/** R13: sent (fee in the same coin), the fee in coins and its USD value, and a link to the fee's taxable event. */
@Composable
private fun NetworkFeeFields(tx: Transaction, onOpenFeeEvent: (() -> Unit)?) {
    val feeAsset = tx.networkFeeAsset ?: return
    val fee = tx.feeQuantity ?: return
    if (feeAsset == tx.asset) LabeledText(stringResource(R.string.label_amount_sent), QuantityFormatter.full(tx.quantity + fee))
    LabeledText(stringResource(R.string.label_network_fee), stringResource(R.string.quantity_asset, QuantityFormatter.full(fee), feeAsset))
    LabeledText(stringResource(R.string.label_network_fee_value), MoneyFormatter.format(tx.fee))
    Text(
        stringResource(R.string.network_fee_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    onOpenFeeEvent?.let { TextButton(onClick = it) { Text(stringResource(R.string.view_fee_event)) } }
}

@PreviewLightDark
@Composable
private fun TransferWithFeePreview() {
    val tx = PreviewData.transactions.first { it.networkFeeAsset == it.asset }
    BlockGainsTheme(dynamicColor = false) {
        TransactionDetailContent(TransactionDetailUiState.Content(tx, hasFeeEvent = true), onBack = {})
    }
}

@PreviewLightDark
@Composable
private fun TransactionDetailPreview() {
    BlockGainsTheme(dynamicColor = false) {
        TransactionDetailContent(TransactionDetailUiState.Content(PreviewData.transactions.first { it.toAsset != null }), onBack = {})
    }
}
