package com.tiffles.blockgains.ui.settings

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.R
import com.tiffles.blockgains.data.export.BackupCodec
import com.tiffles.blockgains.data.files.DocumentFiles
import com.tiffles.blockgains.data.files.NamedDocument
import com.tiffles.blockgains.domain.calc.d
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.usecase.ImportPreview
import com.tiffles.blockgains.domain.usecase.RenameWalletUseCase
import com.tiffles.blockgains.ui.FakeLedgerSource
import com.tiffles.blockgains.ui.FakeSettingsRepository
import com.tiffles.blockgains.ui.FakeTransactionRepository
import com.tiffles.blockgains.ui.MainDispatcherRule
import com.tiffles.blockgains.ui.PendingImport
import com.tiffles.blockgains.ui.SharedSelection
import com.tiffles.blockgains.ui.UserMessage
import com.tiffles.blockgains.ui.UserMessages
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Settings → Data: what each delete removes and what it keeps. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDeleteTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val customized = AppSettings(
        defaultWallet = "FAKE-WALLET-2",
        walletLabels = setOf("FAKE-WALLET-2", "FAKE-EXCHANGE-1"),
        themeMode = ThemeMode.DARK,
        dynamicColor = true,
    )
    private val records = listOf(
        Transaction(type = TransactionType.BUY, date = d("2024-01-01"), asset = "FAKEA", wallet = "FAKE-WALLET-2", quantity = BigDecimal.ONE, priceUsd = BigDecimal("100")),
        Transaction(type = TransactionType.SELL, date = d("2024-06-01"), asset = "FAKEA", wallet = "FAKE-WALLET-2", quantity = BigDecimal.ONE, priceUsd = BigDecimal("150")),
    )

    private val transactions = FakeTransactionRepository(records)
    private val settings = FakeSettingsRepository(customized)
    private val selection = SharedSelection().apply {
        selectYear(2024)
        selectFilter(AssetFilter.CRYPTO)
    }
    private val pending = PendingImport().apply { set(ImportPreview(emptyList(), emptyList(), emptyList(), emptyList(), null, null)) }
    private val messages = UserMessages()

    private val noFiles = object : DocumentFiles {
        override suspend fun readText(uri: Uri): Result<NamedDocument> = error("not used")
        override suspend fun write(uri: Uri, writer: (OutputStream) -> Unit): Result<Unit> = error("not used")
        override suspend fun displayName(uri: Uri): String? = null
    }

    private fun viewModel() = SettingsViewModel(
        settings = settings,
        renameWallet = RenameWalletUseCase(transactions, settings),
        ledgerSource = FakeLedgerSource().apply { set(records) },
        transactions = transactions,
        selection = selection,
        pendingImport = pending,
        documents = noFiles,
        backups = BackupCodec(),
        messages = messages,
        clock = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun deleteTransactions_emptiesTheTable_andKeepsEverySetting() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        val shown = mutableListOf<UserMessage>()
        backgroundScope.launch(Dispatchers.Unconfined) { messages.messages.toList(shown) }
        vm.delete(DeleteScope.TRANSACTIONS)

        assertThat(transactions.records.value).isEmpty()
        assertThat(settings.state.value).isEqualTo(customized)
        assertThat(pending.preview.value).isNull()
        assertThat(selection.year.value).isNull()
        assertThat(selection.filter.value).isEqualTo(AssetFilter.ALL)
        assertThat(shown).containsExactly(UserMessage.Text(R.string.delete_done_transactions))
        assertThat(vm.deleted.first()).isEqualTo(Unit)
    }

    @Test
    fun deleteAllData_emptiesTheTable_andResetsEverySettingToFreshInstall() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        val shown = mutableListOf<UserMessage>()
        backgroundScope.launch(Dispatchers.Unconfined) { messages.messages.toList(shown) }
        vm.delete(DeleteScope.ALL_DATA)

        assertThat(transactions.records.value).isEmpty()
        assertThat(settings.state.value).isEqualTo(AppSettings())
        assertThat(pending.preview.value).isNull()
        assertThat(shown).containsExactly(UserMessage.Text(R.string.delete_done_all))
        assertThat(vm.deleted.first()).isEqualTo(Unit)
    }

    @Test
    fun nothingIsDeleted_untilDeleteIsCalled() = runTest(UnconfinedTestDispatcher()) {
        viewModel()
        assertThat(transactions.records.value).hasSize(2)
        assertThat(settings.state.value).isEqualTo(customized)
    }
}
