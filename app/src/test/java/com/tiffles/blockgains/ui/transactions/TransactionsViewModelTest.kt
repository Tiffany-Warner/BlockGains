package com.tiffles.blockgains.ui.transactions

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.TxBuilder
import com.tiffles.blockgains.domain.calc.assertCents
import com.tiffles.blockgains.domain.model.HoldingTerm
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
class TransactionsViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val ledger = FakeLedgerSource()
    private val selection = SharedSelection()
    private val clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(tab: TransactionsTab = TransactionsTab.ALL) =
        TransactionsViewModel(TransactionFilters(tab = tab), ledger, selection, clock, mainRule.dispatcher)

    private fun TransactionsViewModel.content() = state.value as TransactionsUiState.Content

    @Test
    fun walletRename_updatesAnActiveWalletFilter_andLeavesOthersAlone() = runTest(UnconfinedTestDispatcher()) {
        ledger.set(
            TxBuilder().apply {
                buy("2025-01-02", qty = "1", price = "100", wallet = "FAKE-WALLET-1")
                buy("2025-01-03", qty = "1", price = "100", wallet = "FAKE-WALLET-2")
            }.transactions,
        )
        val vm = viewModel()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }
        vm.selectWallet("FAKE-WALLET-2")

        selection.walletRenamed("FAKE-WALLET-1", "FAKE-WALLET-8")
        assertThat(vm.content().filters.wallet).isEqualTo("FAKE-WALLET-2")

        selection.walletRenamed("FAKE-WALLET-2", "FAKE-WALLET-9")
        ledger.set(
            TxBuilder().apply {
                buy("2025-01-02", qty = "1", price = "100", wallet = "FAKE-WALLET-8")
                buy("2025-01-03", qty = "1", price = "100", wallet = "FAKE-WALLET-9")
            }.transactions,
        )
        assertThat(vm.content().filters.wallet).isEqualTo("FAKE-WALLET-9")
        assertThat(vm.content().records.map { it.wallet }).containsExactly("FAKE-WALLET-9")
    }

    @Test
    fun termFilter_keepsMixedSalesAsOneRow_whosePartsAddUpToTheTermTotals() = runTest(UnconfinedTestDispatcher()) {
        ledger.set(
            TxBuilder().apply {
                buy("2023-01-02", qty = "1", price = "100")
                buy("2025-01-02", qty = "1", price = "200")
                sell("2025-06-01", qty = "2", price = "300") // 1 long-term (+200), 1 short-term (+100)
                buy("2025-02-01", qty = "1", price = "50", asset = "FAKEB")
                sell("2025-03-01", qty = "1", price = "40", asset = "FAKEB") // short-term (-10)
            }.transactions,
        )
        val vm = viewModel(TransactionsTab.EVENTS)
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }
        selection.selectYear(2025)

        vm.selectTerm(HoldingTerm.SHORT)
        val short = vm.content().events
        assertThat(short).hasSize(2)
        val mixed = short.single { it.asset == "FAKEA" }
        assertThat(mixed.term).isNull() // still one row, still Mixed
        assertCents(mixed.termPart(HoldingTerm.SHORT).proceeds, "300.00")
        assertCents(mixed.termPart(HoldingTerm.SHORT).basis, "200.00")
        assertCents(short.sumOf { it.termPart(HoldingTerm.SHORT).gain }, "90.00")

        vm.selectTerm(HoldingTerm.LONG)
        val long = vm.content().events
        assertThat(long.map { it.asset }).containsExactly("FAKEA")
        assertCents(long.sumOf { it.termPart(HoldingTerm.LONG).gain }, "200.00")

        // No term filter: the whole sale.
        assertCents(mixed.termPart(null).gain, "300.00")
    }
}
