package com.tiffles.blockgains.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.tiffles.blockgains.domain.model.LotMatch
import com.tiffles.blockgains.domain.model.MatchingScope
import com.tiffles.blockgains.domain.model.TaxableEvent
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.ui.components.BackButton
import com.tiffles.blockgains.ui.components.EmptyState
import com.tiffles.blockgains.ui.components.LabeledText
import com.tiffles.blockgains.ui.components.LabeledValue
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.components.MarkerChip
import com.tiffles.blockgains.ui.components.MoneyText
import com.tiffles.blockgains.ui.components.SectionHeader
import com.tiffles.blockgains.ui.components.TermChip
import com.tiffles.blockgains.ui.format.DateFormatter
import com.tiffles.blockgains.ui.format.MoneyFormatter
import com.tiffles.blockgains.ui.format.QuantityFormatter
import com.tiffles.blockgains.ui.navigation.Route
import com.tiffles.blockgains.ui.preview.PreviewData
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data object NotFound : EventDetailUiState
    /** [buyFeeLots]: source transaction ids of matched lots whose basis includes a buy fee (R7). */
    data class Content(val event: TaxableEvent, val buyFeeLots: Set<Long> = emptySet()) : EventDetailUiState
}

@HiltViewModel
class EventDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, ledgerSource: LedgerSource) : ViewModel() {
    private val id = savedStateHandle.toRoute<Route.EventDetail>().transactionId

    val state: StateFlow<EventDetailUiState> = ledgerSource.ledger.map { ledger ->
        when (val event = ledger?.result?.taxableEvents?.find { it.transactionId == id }) {
            null -> if (ledger == null) EventDetailUiState.Loading else EventDetailUiState.NotFound
            else -> EventDetailUiState.Content(event, buyFeeLots(ledger.transactions, event))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState.Loading)
}

/** Lots of [event] that came from a BUY with a fee: that fee is part of their basis (R7). Transferred lots keep their source id. */
internal fun buyFeeLots(transactions: List<Transaction>, event: TaxableEvent): Set<Long> {
    val ids = event.lots.mapTo(HashSet()) { it.sourceTransactionId }
    return transactions.filter { it.id in ids && it.type == TransactionType.BUY && (it.feeUsd?.signum() ?: 0) > 0 }.mapTo(HashSet()) { it.id }
}

@Composable
fun EventDetailScreen(onBack: () -> Unit, viewModel: EventDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EventDetailContent(state, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailContent(state: EventDetailUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.event_detail_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        when (state) {
            EventDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            EventDetailUiState.NotFound -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.detail_not_found),
                body = null,
                modifier = Modifier.padding(padding),
            )
            is EventDetailUiState.Content -> EventDetailBody(state.event, state.buyFeeLots, Modifier.padding(padding))
        }
    }
}

@Composable
private fun EventDetailBody(event: TaxableEvent, buyFeeLots: Set<Long>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (event.isTransferFee) {
            Text(stringResource(R.string.transfer_fee_event_title, DateFormatter.format(event.date)), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.quantity_asset, QuantityFormatter.full(event.quantity), event.asset),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.network_fee_helper), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(eventTitle(event, QuantityFormatter.full(event.quantity)), style = MaterialTheme.typography.headlineSmall)
        }
        event.receivedAsset?.let { Text(stringResource(R.string.swap_received, it), style = MaterialTheme.typography.bodyMedium) }
        LabeledText(stringResource(R.string.label_date_sold), DateFormatter.format(event.date))
        LabeledText(stringResource(R.string.label_wallet), event.wallet)
        LabeledText(stringResource(R.string.label_proceeds), MoneyFormatter.format(event.proceeds))
        LabeledText(
            stringResource(R.string.label_basis),
            MoneyFormatter.format(event.basis),
            supporting = stringResource(R.string.basis_includes_buy_fee).takeIf { event.lots.any { it.sourceTransactionId in buyFeeLots } },
        )
        if (!event.isTransferFee) SellingFee(event.fee)
        LabeledValue(stringResource(R.string.label_gain)) { MoneyText(event.gain, signed = true) }
        LabeledText(stringResource(R.string.rule_label), ruleText(event))
        SectionHeader(pluralStringResource(R.plurals.matched_lots, event.lots.size, event.lots.size))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            event.lots.forEach { LotCard(it, event.asset, showSellingFee = !event.isTransferFee, includesBuyFee = it.sourceTransactionId in buyFeeLots) }
        }
    }
}

@Composable
private fun ruleText(event: TaxableEvent): String = when (event.scope) {
    MatchingScope.UNIVERSAL -> stringResource(R.string.rule_universal)
    MatchingScope.PER_WALLET -> stringResource(R.string.rule_per_wallet, event.wallet)
}

/** R7: the SELL/SWAP fee share, already taken out of proceeds. Transfer-fee events have none (R13). */
@Composable
private fun SellingFee(fee: BigDecimal) {
    LabeledText(stringResource(R.string.label_selling_fee), MoneyFormatter.format(fee), supporting = stringResource(R.string.selling_fee_supporting))
}

@Composable
private fun LotCard(lot: LotMatch, asset: String, showSellingFee: Boolean, includesBuyFee: Boolean) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.lot_acquired, lot.acquiredDate?.let(DateFormatter::format) ?: stringResource(R.string.date_unknown)), style = MaterialTheme.typography.titleSmall)
                TermChip(lot.term)
                if (lot.basisUnknown) MarkerChip(stringResource(R.string.basis_unknown_marker))
            }
            Text(stringResource(R.string.lot_wallet, lot.wallet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledText(stringResource(R.string.label_quantity), stringResource(R.string.quantity_asset, QuantityFormatter.full(lot.quantity), asset))
            LabeledText(stringResource(R.string.label_proceeds), MoneyFormatter.format(lot.proceeds))
            LabeledText(
                stringResource(R.string.label_basis),
                MoneyFormatter.format(lot.basis),
                supporting = stringResource(R.string.basis_includes_buy_fee).takeIf { includesBuyFee },
            )
            if (showSellingFee) SellingFee(lot.fee)
            LabeledValue(stringResource(R.string.label_gain)) { MoneyText(lot.gain, signed = true) }
        }
    }
}

@PreviewLightDark
@Composable
private fun EventDetailPreview() {
    val event = PreviewData.result.taxableEvents.maxBy { it.lots.size }
    val buyFeeLots = buyFeeLots(PreviewData.transactions, event)
    BlockGainsTheme(dynamicColor = false) { EventDetailContent(EventDetailUiState.Content(event, buyFeeLots), onBack = {}) }
}

@PreviewLightDark
@Composable
private fun TransferFeeEventDetailPreview() {
    val event = PreviewData.result.taxableEvents.first { it.isTransferFee }
    BlockGainsTheme(dynamicColor = false) { EventDetailContent(EventDetailUiState.Content(event), onBack = {}) }
}
