package com.tiffles.blockgains.ui.importexport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.R
import com.tiffles.blockgains.data.export.Backup
import com.tiffles.blockgains.data.export.BackupCodec
import com.tiffles.blockgains.data.export.PdfReportExporter
import com.tiffles.blockgains.data.export.TaxYearCsvExporter
import com.tiffles.blockgains.data.export.TaxYearData
import com.tiffles.blockgains.data.export.UnifiedCsvExporter
import com.tiffles.blockgains.data.files.DocumentFiles
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.repository.SettingsRepository
import com.tiffles.blockgains.domain.repository.TransactionRepository
import com.tiffles.blockgains.domain.usecase.activityYears
import com.tiffles.blockgains.domain.usecase.selectedYear
import com.tiffles.blockgains.ui.SharedSelection
import com.tiffles.blockgains.ui.UserMessage
import com.tiffles.blockgains.ui.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The six exports in ui-guidelines → Export. */
enum class ExportKind { TAXABLE_EVENTS, ALL_TRANSACTIONS, FORM_8949, INCOME, PDF_REPORT, BACKUP }

/** A decoded backup waiting for the user to confirm it should replace current data. */
data class RestorePreview(val backup: Backup, val firstDate: LocalDate?, val lastDate: LocalDate?)

data class ExportUiState(
    val year: Int,
    val years: List<Int>,
    val filter: AssetFilter,
    val hasRecords: Boolean,
    val today: LocalDate,
    val busy: Boolean = false,
    val restore: RestorePreview? = null,
    /** Unresolved shortfall issues up to the end of [year] for [filter]; > 0 shows the warning card. */
    val shortfalls: Int = 0,
) {
    /** Suggested file name; tax-year exports include the year (ui-guidelines → Export). */
    fun fileName(kind: ExportKind): String = when (kind) {
        ExportKind.TAXABLE_EVENTS -> "blockgains-$year-taxable-events.csv"
        ExportKind.ALL_TRANSACTIONS -> "blockgains-all-transactions-$today.csv"
        ExportKind.FORM_8949 -> "blockgains-$year-form-8949.csv"
        ExportKind.INCOME -> "blockgains-$year-income.csv"
        ExportKind.PDF_REPORT -> "blockgains-$year-report.pdf"
        ExportKind.BACKUP -> "blockgains-backup-$today.json"
    }
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val ledgerSource: LedgerSource,
    private val selection: SharedSelection,
    private val settings: SettingsRepository,
    private val transactions: TransactionRepository,
    private val documents: DocumentFiles,
    private val csv: TaxYearCsvExporter,
    private val unified: UnifiedCsvExporter,
    private val pdf: PdfReportExporter,
    private val backups: BackupCodec,
    private val messages: UserMessages,
    private val clock: Clock,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val restore = MutableStateFlow<RestorePreview?>(null)

    val state: StateFlow<ExportUiState?> = combine(ledgerSource.ledger, selection.year, selection.filter, busy, restore) { ledger, choice, filter, busy, restore ->
        ledger?.let {
            val years = activityYears(it.transactions)
            val today = today()
            val year = selectedYear(choice, years, today.year)
            val shortfalls = TaxYearData.shortfallsThrough(it.result, it.transactions, year, filter).size
            ExportUiState(year, years, filter, it.transactions.isNotEmpty(), today, busy, restore, shortfalls)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun today(): LocalDate = LocalDate.now(clock)

    fun selectYear(year: Int) = selection.selectYear(year)
    fun selectFilter(filter: AssetFilter) = selection.selectFilter(filter)

    fun export(kind: ExportKind, uri: Uri) = runWrite(uri) { out ->
        val ledger = ledgerSource.ledger.filterNotNull().first()
        val current = checkNotNull(state.value)
        val year = TaxYearData.from(ledger.result, ledger.transactions, current.year, current.filter)
        when (kind) {
            ExportKind.TAXABLE_EVENTS -> out.writeText(csv.taxableEvents(year))
            ExportKind.ALL_TRANSACTIONS -> out.writeText(unified.export(ledger.transactions))
            ExportKind.FORM_8949 -> out.writeText(csv.form8949(year))
            ExportKind.INCOME -> out.writeText(csv.income(year))
            ExportKind.PDF_REPORT -> pdf.write(year, today(), out)
            ExportKind.BACKUP -> out.writeText(backups.encode(ledger.transactions, settings.settings.first(), Instant.now(clock)))
        }
    }

    /** Reads a backup and shows what restoring it would do. Nothing changes until [confirmRestore]. */
    fun loadRestore(uri: Uri) {
        busy.value = true
        viewModelScope.launch {
            val backup = documents.readText(uri).mapCatching { backups.decode(it.text).getOrThrow() }.getOrNull()
            busy.value = false
            if (backup == null) {
                messages.show(UserMessage.Text(R.string.restore_read_error))
                return@launch
            }
            val dates = backup.transactions.map { it.date }
            restore.value = RestorePreview(backup, dates.minOrNull(), dates.maxOrNull())
        }
    }

    fun confirmRestore() {
        val preview = restore.value ?: return
        restore.value = null
        busy.value = true
        viewModelScope.launch {
            transactions.replaceAll(preview.backup.transactions)
            settings.replaceAll(preview.backup.settings)
            busy.value = false
            messages.show(UserMessage.Plural(R.plurals.restored_message, preview.backup.transactions.size))
        }
    }

    fun dismissRestore() {
        restore.value = null
    }

    private fun runWrite(uri: Uri, write: suspend (OutputStream) -> Unit) {
        busy.value = true
        viewModelScope.launch {
            // Build the content first (suspending), then hand bytes to the blocking SAF write.
            val bytes = runCatching { java.io.ByteArrayOutputStream().also { write(it) }.toByteArray() }
            val result = bytes.mapCatching { content -> documents.write(uri) { it.write(content) }.getOrThrow() }
            busy.value = false
            messages.show(UserMessage.Text(if (result.isSuccess) R.string.export_saved else R.string.file_save_error))
        }
    }

    private fun OutputStream.writeText(text: String) = write(text.toByteArray(Charsets.UTF_8))
}
