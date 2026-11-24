package com.tiffles.blockgains.ui.summary

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.TxBuilder
import com.tiffles.blockgains.domain.calc.assertCents
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import com.tiffles.blockgains.ui.FakeLedgerSource
import com.tiffles.blockgains.ui.MainDispatcherRule
import com.tiffles.blockgains.ui.SharedSelection
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val ledger = FakeLedgerSource()
    private val selection = SharedSelection()
    private val clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC)

    private fun viewModel() = SummaryViewModel(ledger, selection, SummarizeYearUseCase(), clock, mainRule.dispatcher)

    private val records = TxBuilder().apply {
        buy("2023-01-02", qty = "1", price = "100", asset = "FAKEA")
        sell("2023-06-01", qty = "1", price = "300", asset = "FAKEA")
        buy("2024-01-02", qty = "10", price = "50", asset = "FAKEE", assetClass = AssetClass.STOCK)
        sell("2024-05-01", qty = "10", price = "40", asset = "FAKEE", assetClass = AssetClass.STOCK)
        buy("2024-01-03", qty = "1", price = "100", asset = "FAKEB")
        sell("2024-05-02", qty = "1", price = "150", asset = "FAKEB")
    }.transactions

    @Test
    fun loading_thenEmpty_thenContent() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }
        assertThat(vm.state.value).isEqualTo(SummaryUiState.Loading)

        ledger.set(emptyList())
        assertThat(vm.state.value).isEqualTo(SummaryUiState.Empty)

        ledger.set(records)
        assertThat(vm.state.value).isInstanceOf(SummaryUiState.Content::class.java)
    }

    @Test
    fun defaultsToLatestYearWithData_whenCurrentYearHasNone() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }
        ledger.set(records)

        val content = vm.state.value as SummaryUiState.Content
        assertThat(content.years).containsExactly(2024, 2023).inOrder()
        assertThat(content.summary.year).isEqualTo(2024)
        assertCents(content.summary.net, "-50") // stocks −100, crypto +50 net together (R11)
        assertThat(content.summary.showsLossLimitNote).isTrue()
    }

    @Test
    fun yearAndFilterChoices_areSharedAndApplied() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }
        ledger.set(records)

        vm.selectFilter(AssetFilter.CRYPTO)
        assertCents((vm.state.value as SummaryUiState.Content).summary.net, "50")

        vm.selectYear(2023)
        val content = vm.state.value as SummaryUiState.Content
        assertThat(content.summary.year).isEqualTo(2023)
        assertCents(content.summary.shortTerm, "200")
        assertThat(selection.year.value).isEqualTo(2023)
    }
}
