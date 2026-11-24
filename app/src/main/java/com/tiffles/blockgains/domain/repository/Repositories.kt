package com.tiffles.blockgains.domain.repository

import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/** Raw records. Lots and events are derived, never stored. */
interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>

    suspend fun getAll(): List<Transaction>

    suspend fun add(transactions: List<Transaction>)

    /** Deletes everything and inserts [transactions] atomically (restore). */
    suspend fun replaceAll(transactions: List<Transaction>)

    /** Replaces every OPENING record of [asset] atomically (Opening positions helper). */
    suspend fun replaceOpenings(asset: String, openings: List<Transaction>)

    /** Renames a wallet label in `wallet` and `to_wallet` of every record. */
    suspend fun renameWallet(from: String, to: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDefaultWallet(wallet: String)

    suspend fun addWallet(label: String)

    /** Renames the label in settings (records are renamed by [TransactionRepository.renameWallet]). */
    suspend fun renameWallet(from: String, to: String)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    /** Restore from backup. */
    suspend fun replaceAll(settings: AppSettings)

    /** Clears every setting, so each one reads its fresh-install default (Settings → Delete all data). */
    suspend fun resetAll()
}
