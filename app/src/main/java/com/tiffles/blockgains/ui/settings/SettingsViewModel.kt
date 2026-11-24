package com.tiffles.blockgains.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.R
import com.tiffles.blockgains.data.export.BackupCodec
import com.tiffles.blockgains.data.files.DocumentFiles
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.repository.SettingsRepository
import com.tiffles.blockgains.domain.repository.TransactionRepository
import com.tiffles.blockgains.domain.usecase.RenameWalletUseCase
import com.tiffles.blockgains.ui.PendingImport
import com.tiffles.blockgains.ui.SharedSelection
import com.tiffles.blockgains.ui.UserMessage
import com.tiffles.blockgains.ui.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings,
    /** Labels from settings plus any wallet used in records. */
    val wallets: List<String>,
    /** Stored records, for the delete confirmation. */
    val transactionCount: Int = 0,
    /** Suggested name for the Full backup (JSON) offered before deleting (same as Export). */
    val backupFileName: String = "blockgains-backup.json",
)

/** What a Settings → Data delete removes. */
enum class DeleteScope {
    /** The transactions table only; settings stay. */
    TRANSACTIONS,

    /** Every transaction plus every setting, back to a fresh install. */
    ALL_DATA,
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val renameWallet: RenameWalletUseCase,
    ledgerSource: LedgerSource,
    private val transactions: TransactionRepository,
    private val selection: SharedSelection,
    private val pendingImport: PendingImport,
    private val documents: DocumentFiles,
    private val backups: BackupCodec,
    private val messages: UserMessages,
    private val clock: Clock,
) : ViewModel() {

    private val _deleted = Channel<Unit>(Channel.BUFFERED)

    /** One-shot: data was deleted; leave Settings for the (now empty) Summary. */
    val deleted: Flow<Unit> = _deleted.receiveAsFlow()

    val state: StateFlow<SettingsUiState?> = combine(settings.settings, ledgerSource.ledger) { appSettings, ledger ->
        val recordWallets = ledger?.transactions?.flatMap { listOfNotNull(it.wallet, it.toWallet) }.orEmpty()
        SettingsUiState(
            settings = appSettings,
            wallets = (appSettings.walletLabels + recordWallets).sorted(),
            transactionCount = ledger?.transactions?.size ?: 0,
            backupFileName = "blockgains-backup-${LocalDate.now(clock)}.json",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDefaultWallet(wallet: String) = launch { settings.setDefaultWallet(wallet) }

    /** False if the label is blank or already used. */
    fun addWallet(label: String): Boolean {
        val trimmed = label.trim()
        if (!isAvailable(trimmed)) return false
        launch { settings.addWallet(trimmed) }
        return true
    }

    fun renameWallet(from: String, to: String): Boolean {
        val trimmed = to.trim()
        if (trimmed == from) return true
        if (!isAvailable(trimmed)) return false
        launch {
            renameWallet.invoke(from, trimmed)
            selection.walletRenamed(from, trimmed)
        }
        return true
    }

    fun setThemeMode(mode: ThemeMode) = launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { settings.setDynamicColor(enabled) }

    /** Full backup (JSON), the same file Export → Full backup writes. Offered before deleting. */
    fun backup(uri: Uri) = launch {
        val text = backups.encode(transactions.getAll(), settings.settings.first(), Instant.now(clock))
        val result = documents.write(uri) { it.write(text.toByteArray(Charsets.UTF_8)) }
        messages.show(UserMessage.Text(if (result.isSuccess) R.string.export_saved else R.string.file_save_error))
    }

    /** Settings → Data. Called only after both confirmation steps. */
    fun delete(scope: DeleteScope) = launch {
        transactions.replaceAll(emptyList())
        if (scope == DeleteScope.ALL_DATA) settings.resetAll()
        pendingImport.clear()
        selection.reset()
        val message = when (scope) {
            DeleteScope.TRANSACTIONS -> R.string.delete_done_transactions
            DeleteScope.ALL_DATA -> R.string.delete_done_all
        }
        messages.show(UserMessage.Text(message))
        _deleted.send(Unit)
    }

    private fun isAvailable(label: String): Boolean = label.isNotEmpty() && state.value?.wallets?.contains(label) != true

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
