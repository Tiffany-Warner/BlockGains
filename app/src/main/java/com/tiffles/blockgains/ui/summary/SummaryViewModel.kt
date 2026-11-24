package com.tiffles.blockgains.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.di.DefaultDispatcher
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import com.tiffles.blockgains.domain.usecase.YearSummary
import com.tiffles.blockgains.domain.usecase.activityYears
import com.tiffles.blockgains.domain.usecase.selectedYear
import com.tiffles.blockgains.ui.SharedSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

sealed interface SummaryUiState {
    data object Loading : SummaryUiState
    data object Empty : SummaryUiState
    data class Content(
        val years: List<Int>,
        val filter: AssetFilter,
        val summary: YearSummary,
    ) : SummaryUiState
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    ledgerSource: LedgerSource,
    private val selection: SharedSelection,
    summarize: SummarizeYearUseCase,
    clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<SummaryUiState> = combine(ledgerSource.ledger, selection.year, selection.filter) { ledger, choice, filter ->
        when {
            ledger == null -> SummaryUiState.Loading
            ledger.transactions.isEmpty() -> SummaryUiState.Empty
            else -> {
                val years = activityYears(ledger.transactions)
                val year = selectedYear(choice, years, LocalDate.now(clock).year)
                SummaryUiState.Content(years, filter, summarize(ledger.result, year, filter))
            }
        }
    }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState.Loading)

    fun selectYear(year: Int) = selection.selectYear(year)

    fun selectFilter(filter: AssetFilter) = selection.selectFilter(filter)
}
