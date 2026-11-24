package com.tiffles.blockgains.domain.usecase

import com.tiffles.blockgains.domain.repository.SettingsRepository
import com.tiffles.blockgains.domain.repository.TransactionRepository
import javax.inject.Inject

/** Renames a wallet label in every record and in settings (ui-guidelines → Settings). */
class RenameWalletUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(from: String, to: String) {
        val label = to.trim()
        if (label.isEmpty() || label == from) return
        transactions.renameWallet(from, label)
        settings.renameWallet(from, label)
    }
}
