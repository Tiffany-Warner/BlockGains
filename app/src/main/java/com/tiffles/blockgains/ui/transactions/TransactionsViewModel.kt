package com.tiffles.blockgains.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tiffles.blockgains.di.DefaultDispatcher
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.TaxableEvent
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.repository.Ledger
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.usecase.activityYears
import com.tiffles.blockgains.domain.usecase.selectedYear
import com.tiffles.blockgains.ui.SharedSelection
import com.tiffles.blockgains.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TransactionsTab { EVENTS, ALL }

/**
 * What a row shows under a term filter: for a mixed disposal, only the lots of [term], so its
 * proceeds, basis and gain add up to that term's total. Unfiltered or single-term events are unchanged.
 */
fun TaxableEvent.termPart(term: HoldingTerm?): TaxableEvent =
    if (term == null || this.term != null) this else copy(lots = lots.filter { it.term == term })

/** Screen-local filters (asset class and year are shared via [SharedSelection]). */
data class TransactionFilters(
    val tab: TransactionsTab = TransactionsTab.EVENTS,
    val term: HoldingTerm? = null,
    val type: TransactionType? = null,
    val asset: String? = null,
    val wallet: String? = null,
    val query: String = "",
) {
    val anyRecordFilter: Boolean get() = type != null || asset != null || wallet != null || query.isNotBlank()
}

sealed interface TransactionsUiState {
    data object Loading : TransactionsUiState
    data class Content(
        val year: Int,
        val years: List<Int>,
        val assetFilter: AssetFilter,
        val filters: TransactionFilters,
        /** Selected year's taxable events, newest first, after the term filter. */
        val events: List<TaxableEvent>,
        /** All records, newest first, after type/asset/wallet/search filters. */
        val records: List<Transaction>,
        val hasAnyRecords: Boolean,
        val assets: List<String>,
        val wallets: List<String>,
    ) : TransactionsUiState
}

@HiltViewModel
class TransactionsViewModel internal constructor(
    initial: TransactionFilters,
    ledgerSource: LedgerSource,
    private val selection: SharedSelection,
    private val clock: Clock,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject constructor(
        savedStateHandle: SavedStateHandle,
        ledgerSource: LedgerSource,
        selection: SharedSelection,
        clock: Clock,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(initialFilters(savedStateHandle.toRoute<Route.Transactions>()), ledgerSource, selection, clock, dispatcher)

    private val filters = MutableStateFlow(initial)

    val state: StateFlow<TransactionsUiState> = combine(
        ledgerSource.ledger, selection.year, selection.filter, filters,
    ) { ledger, choice, assetFilter, filters ->
        if (ledger == null) TransactionsUiState.Loading else content(ledger, choice, assetFilter, filters)
    }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState.Loading)

    init {
        // An active wallet filter follows a rename in Settings instead of matching nothing.
        viewModelScope.launch {
            selection.walletRenames.collect { (from, to) -> filters.update { if (it.wallet == from) it.copy(wallet = to) else it } }
        }
    }

    fun selectYear(year: Int) = selection.selectYear(year)
    fun selectAssetFilter(filter: AssetFilter) = selection.selectFilter(filter)
    fun selectTab(tab: TransactionsTab) = filters.update { it.copy(tab = tab) }
    fun selectTerm(term: HoldingTerm?) = filters.update { it.copy(term = term) }
    fun selectType(type: TransactionType?) = filters.update { it.copy(type = type) }
    fun selectAsset(asset: String?) = filters.update { it.copy(asset = asset) }
    fun selectWallet(wallet: String?) = filters.update { it.copy(wallet = wallet) }
    fun search(query: String) = filters.update { it.copy(query = query) }
    fun clearRecordFilters() = filters.update { it.copy(type = null, asset = null, wallet = null, query = "") }

    private fun content(ledger: Ledger, choice: Int?, assetFilter: AssetFilter, filters: TransactionFilters): TransactionsUiState.Content {
        val years = activityYears(ledger.transactions)
        val year = selectedYear(choice, years, LocalDate.now(clock).year)
        val inClass = ledger.transactions.filter { assetFilter.matches(it.assetClass) }
        return TransactionsUiState.Content(
            year = year,
            years = years,
            assetFilter = assetFilter,
            filters = filters,
            events = ledger.result.taxableEvents
                .filter { it.date.year == year && assetFilter.matches(it.assetClass) && matchesTerm(it, filters.term) }
                .sortedByDescending { it.date },
            records = inClass.filter { matchesRecord(it, filters) }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id }),
            hasAnyRecords = inClass.isNotEmpty(),
            assets = inClass.flatMap { listOfNotNull(it.asset, it.toAsset) }.distinct().sorted(),
            wallets = inClass.flatMap { listOfNotNull(it.wallet, it.toWallet) }.distinct().sorted(),
        )
    }

    /** A term filter keeps events with any lot of that term (mixed disposals count for both; rows show [termPart]). */
    private fun matchesTerm(event: TaxableEvent, term: HoldingTerm?) = term == null || event.lots.any { it.term == term }

    private fun matchesRecord(tx: Transaction, f: TransactionFilters): Boolean =
        (f.type == null || tx.type == f.type) &&
            (f.asset == null || tx.asset == f.asset || tx.toAsset == f.asset) &&
            (f.wallet == null || tx.wallet == f.wallet || tx.toWallet == f.wallet) &&
            (f.query.isBlank() || tx.note?.contains(f.query.trim(), ignoreCase = true) == true)

    private companion object {
        fun initialFilters(route: Route.Transactions) = TransactionFilters(
            tab = if (route.showAll) TransactionsTab.ALL else TransactionsTab.EVENTS,
            term = route.term?.let { name -> HoldingTerm.entries.find { it.name == name } },
            type = route.type?.let { name -> TransactionType.entries.find { it.name == name } },
        )
    }
}
