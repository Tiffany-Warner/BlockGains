package com.tiffles.blockgains.ui

import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.repository.Ledger
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.repository.SettingsRepository
import com.tiffles.blockgains.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Swaps `Dispatchers.Main` for a test dispatcher so `viewModelScope` runs in tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** In-memory records; ids assigned on insert like Room. */
class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    private var nextId = 1L
    val records = MutableStateFlow(initial.map { it.copy(id = nextId++) })

    override fun observeAll(): Flow<List<Transaction>> = records
    override suspend fun getAll(): List<Transaction> = records.value
    override suspend fun add(transactions: List<Transaction>) = records.update { it + transactions.map { tx -> tx.copy(id = nextId++) } }
    override suspend fun replaceAll(transactions: List<Transaction>) {
        records.value = transactions.map { it.copy(id = nextId++) }
    }
    override suspend fun replaceOpenings(asset: String, openings: List<Transaction>) = records.update { list ->
        list.filterNot { it.type == com.tiffles.blockgains.domain.model.TransactionType.OPENING && it.asset == asset } +
            openings.map { it.copy(id = nextId++) }
    }
    override suspend fun renameWallet(from: String, to: String) = records.update { list ->
        list.map { it.copy(wallet = if (it.wallet == from) to else it.wallet, toWallet = if (it.toWallet == from) to else it.toWallet) }
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun setDefaultWallet(wallet: String) = state.update { it.copy(defaultWallet = wallet, walletLabels = it.walletLabels + wallet) }
    override suspend fun addWallet(label: String) = state.update { it.copy(walletLabels = it.walletLabels + label) }
    override suspend fun renameWallet(from: String, to: String) = state.update {
        it.copy(walletLabels = it.walletLabels - from + to, defaultWallet = if (it.defaultWallet == from) to else it.defaultWallet)
    }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }
    override suspend fun replaceAll(settings: AppSettings) {
        state.value = settings
    }
    override suspend fun resetAll() {
        state.value = AppSettings()
    }
}

/** A ledger that recalculates synchronously whenever [set] is called; null means "still loading". */
class FakeLedgerSource(private val defaultWallet: String = "FAKE-WALLET-1") : LedgerSource {
    private val _ledger = MutableStateFlow<Ledger?>(null)
    override val ledger: StateFlow<Ledger?> = _ledger

    fun set(transactions: List<Transaction>) {
        val result = TaxCalculator().calculate(transactions, CalcSettings(defaultWallet))
        _ledger.value = Ledger(transactions, result, defaultWallet)
    }
}
