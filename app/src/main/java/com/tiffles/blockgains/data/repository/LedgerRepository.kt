package com.tiffles.blockgains.data.repository

import com.tiffles.blockgains.di.ApplicationScope
import com.tiffles.blockgains.di.DefaultDispatcher
import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.repository.Ledger
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.repository.SettingsRepository
import com.tiffles.blockgains.domain.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Runs the calculator on [DefaultDispatcher] whenever records or the default wallet change. */
@Singleton
class LedgerRepository @Inject constructor(
    transactions: TransactionRepository,
    settings: SettingsRepository,
    calculator: TaxCalculator,
    @ApplicationScope scope: CoroutineScope,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : LedgerSource {

    override val ledger: StateFlow<Ledger?> = combine(
        transactions.observeAll(),
        settings.settings.map { it.defaultWallet }.distinctUntilChanged(),
    ) { records, defaultWallet ->
        Ledger(records, calculator.calculate(records, CalcSettings(defaultWallet)), defaultWallet)
    }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
