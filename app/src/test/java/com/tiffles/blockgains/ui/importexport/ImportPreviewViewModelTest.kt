package com.tiffles.blockgains.ui.importexport

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.d
import com.tiffles.blockgains.domain.model.ImportParseResult
import com.tiffles.blockgains.domain.model.ImportedRow
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.usecase.BuildImportPreviewUseCase
import com.tiffles.blockgains.ui.FakeTransactionRepository
import com.tiffles.blockgains.ui.MainDispatcherRule
import com.tiffles.blockgains.ui.PendingImport
import com.tiffles.blockgains.ui.UserMessage
import com.tiffles.blockgains.ui.UserMessages
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportPreviewViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeTransactionRepository()
    private val pending = PendingImport()
    private val messages = UserMessages()
    private val build = BuildImportPreviewUseCase()

    private fun buy(day: Int, price: String) = Transaction(
        type = TransactionType.BUY, date = d("2023-01-%02d".format(day)), asset = "FAKEB", wallet = "FAKE-WALLET-1",
        quantity = BigDecimal.ONE, priceUsd = BigDecimal(price),
    )

    private fun viewModel(vararg rows: Transaction): ImportPreviewViewModel {
        val parsed = ImportParseResult(rows.mapIndexed { i, tx -> ImportedRow(tx, i + 2, "f.csv") }, emptyList())
        pending.set(build(parsed, existing = emptyList()))
        return ImportPreviewViewModel(pending, build, repository, messages)
    }

    @Test
    fun incomeResolution_needsAValidFmv_beforeConfirming() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(buy(1, "100"), buy(2, "0"))
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }

        vm.setIncome(1, true)
        assertThat((vm.state.value as ImportPreviewUiState.Content).canConfirm).isFalse()
        vm.setFmv(1, "abc")
        assertThat((vm.state.value as ImportPreviewUiState.Content).canConfirm).isFalse()
        vm.setFmv(1, "12.50")
        assertThat((vm.state.value as ImportPreviewUiState.Content).canConfirm).isTrue()
    }

    @Test
    fun fmvError_showsOnlyAfterTyping() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(buy(1, "100"), buy(2, "0"))
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }
        fun draft() = (vm.state.value as ImportPreviewUiState.Content).drafts.getValue(1)

        vm.setIncome(1, true)
        assertThat(draft().showError).isFalse()
        assertThat(draft().isValid).isFalse()
        vm.setFmv(1, "-1")
        assertThat(draft().showError).isTrue()
        vm.setFmv(1, "")
        assertThat(draft().showError).isTrue()
        vm.setFmv(1, "10")
        assertThat(draft().showError).isFalse()
    }

    @Test
    fun confirm_writesResolvedRecords_clearsThePreview_andAnnouncesTheCount() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(buy(1, "100"), buy(2, "0"), buy(3, "0"))
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }
        val message = backgroundScope.launch(Dispatchers.Unconfined) {
            assertThat(messages.messages.first()).isEqualTo(UserMessage.Plural(com.tiffles.blockgains.R.plurals.imported_message, 3))
        }
        vm.setIncome(2, true)
        vm.setFmv(2, "20")

        vm.confirm()

        val written = repository.records.value
        assertThat(written).hasSize(3)
        assertThat(written[1].basisUnknown).isTrue() // default: unknown basis
        assertThat(written[2].type).isEqualTo(TransactionType.INCOME)
        assertThat(written[2].usdValue).isEquivalentAccordingToCompareTo(BigDecimal("20"))
        assertThat(pending.preview.value).isNull()
        assertThat(message.isCompleted).isTrue()
    }

    @Test
    fun cancel_discardsThePreview_withoutWriting() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(buy(1, "100"))
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect {} }

        vm.cancel()

        assertThat(repository.records.value).isEmpty()
        assertThat(pending.preview.value).isNull()
    }

    @Test
    fun confirmAndCancel_neverShowTheExpiredStateWhileTheScreenCloses() = runTest(UnconfinedTestDispatcher()) {
        for (leave in listOf<(ImportPreviewViewModel) -> Unit>({ it.confirm() }, { it.cancel() })) {
            val vm = viewModel(buy(1, "100"))
            val states = mutableListOf<ImportPreviewUiState>()
            val collector = backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect { states += it } }
            states.clear()

            leave(vm)

            assertThat(pending.preview.value).isNull()
            assertThat(states).isNotEmpty()
            assertThat(states.none { it == ImportPreviewUiState.Expired }).isTrue()
            assertThat((vm.state.value as ImportPreviewUiState.Content).canConfirm).isFalse()
            collector.cancel()
        }
    }
}
