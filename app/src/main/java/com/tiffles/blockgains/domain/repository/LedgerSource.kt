package com.tiffles.blockgains.domain.repository

import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.Transaction
import kotlinx.coroutines.flow.StateFlow

/** Raw records plus everything derived from them, recomputed whenever records or the default wallet change. */
data class Ledger(
    val transactions: List<Transaction>,
    val result: CalcResult,
    val defaultWallet: String,
)

/** App-wide derived state, shared by every screen so the calculator runs once per change. */
interface LedgerSource {
    /** Null until the first calculation finishes. */
    val ledger: StateFlow<Ledger?>
}
