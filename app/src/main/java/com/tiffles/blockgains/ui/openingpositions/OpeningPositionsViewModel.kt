package com.tiffles.blockgains.ui.openingpositions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.R
import com.tiffles.blockgains.data.importer.ValueParsers
import com.tiffles.blockgains.domain.calc.TaxRules
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.Lot
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.repository.Ledger
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.repository.SettingsRepository
import com.tiffles.blockgains.domain.repository.TransactionRepository
import com.tiffles.blockgains.domain.usecase.LotAllocation
import com.tiffles.blockgains.domain.usecase.OpeningLotDraft
import com.tiffles.blockgains.domain.usecase.OpeningPositionsUseCase
import com.tiffles.blockgains.ui.UserMessage
import com.tiffles.blockgains.ui.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One wallet row as typed: the quantity stays text until it parses. */
data class AllocationInput(val wallet: String, val quantityText: String) {
    fun toAllocation() = LotAllocation(wallet, ValueParsers.decimal(quantityText) ?: BigDecimal.ZERO)
}

data class OpeningAsset(
    val asset: String,
    val lots: List<Lot>,
    val inputs: List<List<AllocationInput>>,
    /** Total in saved OPENING records, or null if none are saved. */
    val savedTotal: BigDecimal?,
    val mismatch: CalcIssue.TransitionMismatch?,
    val edited: Boolean,
) {
    val drafts: List<OpeningLotDraft> get() = lots.zip(inputs) { lot, rows -> OpeningLotDraft(lot, rows.map { it.toAllocation() }) }
    val canSave: Boolean get() = drafts.all { it.problem == null } && (edited || savedTotal == null || mismatch != null)
}

/** OPENING records for an asset with nothing in the universal pool on 2025-01-01. */
data class OrphanOpenings(val asset: String, val total: BigDecimal)

sealed interface OpeningPositionsUiState {
    data object Loading : OpeningPositionsUiState
    data class Content(
        val assets: List<OpeningAsset>,
        val orphans: List<OrphanOpenings>,
        val wallets: List<String>,
        val defaultWallet: String,
    ) : OpeningPositionsUiState
}

@HiltViewModel
class OpeningPositionsViewModel @Inject constructor(
    ledgerSource: LedgerSource,
    settings: SettingsRepository,
    private val transactions: TransactionRepository,
    private val useCase: OpeningPositionsUseCase,
    private val messages: UserMessages,
) : ViewModel() {

    /** Per-asset edits on top of what's derived from saved records. */
    private val edits = MutableStateFlow<Map<String, List<List<AllocationInput>>>>(emptyMap())

    val state: StateFlow<OpeningPositionsUiState> = combine(ledgerSource.ledger, settings.settings, edits) { ledger, appSettings, edits ->
        if (ledger == null) {
            OpeningPositionsUiState.Loading
        } else {
            val recordWallets = ledger.transactions.flatMap { listOfNotNull(it.wallet, it.toWallet) }
            OpeningPositionsUiState.Content(
                assets = assets(ledger, edits),
                orphans = orphans(ledger),
                wallets = (appSettings.walletLabels + recordWallets).sorted(),
                defaultWallet = ledger.defaultWallet,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpeningPositionsUiState.Loading)

    fun setWallet(asset: String, lot: Int, row: Int, wallet: String) = editRow(asset, lot, row) { it.copy(wallet = wallet) }

    fun setQuantity(asset: String, lot: Int, row: Int, text: String) = editRow(asset, lot, row) { it.copy(quantityText = text) }

    /** Adds a wallet row pre-filled with whatever is still unassigned. */
    fun addSplit(asset: String, lot: Int) {
        val defaultWallet = (state.value as? OpeningPositionsUiState.Content)?.defaultWallet ?: return
        editLot(asset, lot) { rows, draft ->
            val left = draft.unassigned.takeIf { it.signum() > 0 }?.stripTrailingZeros()?.toPlainString().orEmpty()
            rows + AllocationInput(defaultWallet, left)
        }
    }

    fun removeSplit(asset: String, lot: Int, row: Int) = editLot(asset, lot) { rows, _ -> rows.filterIndexed { i, _ -> i != row } }

    fun save(asset: String) {
        val target = (state.value as? OpeningPositionsUiState.Content)?.assets?.find { it.asset == asset } ?: return
        if (!target.canSave) return
        viewModelScope.launch {
            transactions.replaceOpenings(asset, useCase.toOpeningRecords(target.drafts))
            edits.update { it - asset }
            messages.show(UserMessage.Text(R.string.opening_saved, listOf(asset)))
        }
    }

    fun removeOrphans(asset: String) {
        viewModelScope.launch { transactions.replaceOpenings(asset, emptyList()) }
    }

    private fun assets(ledger: Ledger, edits: Map<String, List<List<AllocationInput>>>): List<OpeningAsset> {
        val saved = ledger.transactions.filter { it.type == TransactionType.OPENING }.groupBy { it.asset }
        val mismatches = ledger.result.issues.filterIsInstance<CalcIssue.TransitionMismatch>().associateBy { it.asset }
        return ledger.result.universalLotsAtTransition.groupBy { it.asset }.toSortedMap().map { (asset, lots) ->
            val records = saved[asset].orEmpty()
            val base = useCase.drafts(lots, records, ledger.defaultWallet).map { draft ->
                draft.allocations.map { AllocationInput(it.wallet, it.quantity.stripTrailingZeros().toPlainString()) }
            }
            OpeningAsset(
                asset = asset,
                lots = lots,
                inputs = edits[asset] ?: base,
                savedTotal = records.takeIf { it.isNotEmpty() }?.sumOf { it.quantity },
                mismatch = mismatches[asset],
                edited = asset in edits,
            )
        }
    }

    /** R4: OPENING records for an asset whose pre-2025 records leave nothing held on 2025-01-01. */
    private fun orphans(ledger: Ledger): List<OrphanOpenings> {
        val held = ledger.result.universalLotsAtTransition.map { it.asset }.toSet()
        val withHistory = TaxRules.assetsWithHistory(ledger.transactions.filter { it.type != TransactionType.OPENING })
        return ledger.transactions.filter { it.type == TransactionType.OPENING && it.asset !in held && it.asset in withHistory }
            .groupBy { it.asset }
            .map { (asset, records) -> OrphanOpenings(asset, records.sumOf { it.quantity }) }
    }

    private fun editRow(asset: String, lot: Int, row: Int, change: (AllocationInput) -> AllocationInput) =
        editLot(asset, lot) { rows, _ -> rows.mapIndexed { i, input -> if (i == row) change(input) else input } }

    private fun editLot(asset: String, lot: Int, change: (List<AllocationInput>, OpeningLotDraft) -> List<AllocationInput>) {
        val current = (state.value as? OpeningPositionsUiState.Content)?.assets?.find { it.asset == asset } ?: return
        val inputs = current.inputs.mapIndexed { i, rows -> if (i == lot) change(rows, current.drafts[i]) else rows }
        edits.update { it + (asset to inputs) }
    }
}
