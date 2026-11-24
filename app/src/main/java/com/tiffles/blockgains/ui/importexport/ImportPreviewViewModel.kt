package com.tiffles.blockgains.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.R
import com.tiffles.blockgains.data.importer.ValueParsers
import com.tiffles.blockgains.domain.model.ZeroPriceResolution
import com.tiffles.blockgains.domain.repository.TransactionRepository
import com.tiffles.blockgains.domain.usecase.BuildImportPreviewUseCase
import com.tiffles.blockgains.domain.usecase.ImportPreview
import com.tiffles.blockgains.ui.PendingImport
import com.tiffles.blockgains.ui.UserMessage
import com.tiffles.blockgains.ui.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The user's answer for one $0 buy, as typed. Unknown basis is pre-selected (the conservative choice).
 * [edited] turns true on the first keystroke, so the value error only shows once something was typed.
 */
data class ZeroPriceDraft(val asIncome: Boolean = false, val fmvText: String = "", val edited: Boolean = false) {
    val fmv: BigDecimal? get() = ValueParsers.decimal(fmvText)?.takeIf { it.signum() >= 0 }
    val isValid: Boolean get() = !asIncome || fmv != null
    val showError: Boolean get() = edited && !isValid

    fun toResolution(): ZeroPriceResolution? = when {
        !asIncome -> ZeroPriceResolution.UnknownBasis
        else -> fmv?.let { ZeroPriceResolution.Income(it) }
    }
}

sealed interface ImportPreviewUiState {
    data object Expired : ImportPreviewUiState
    data class Content(
        val preview: ImportPreview,
        val drafts: Map<Int, ZeroPriceDraft>,
        val saving: Boolean,
    ) : ImportPreviewUiState {
        val canConfirm: Boolean get() = !saving && !preview.isEmpty && preview.zeroPriceIndices.all { drafts[it]?.isValid ?: true }
    }
}

@HiltViewModel
class ImportPreviewViewModel @Inject constructor(
    private val pendingImport: PendingImport,
    private val buildPreview: BuildImportPreviewUseCase,
    private val transactions: TransactionRepository,
    private val messages: UserMessages,
) : ViewModel() {

    private val drafts = MutableStateFlow<Map<Int, ZeroPriceDraft>>(emptyMap())
    private val saving = MutableStateFlow(false)

    /**
     * Set once the screen is leaving (confirmed or cancelled). The pending preview is cleared
     * then, but the screen keeps showing the last preview while it closes, never "Nothing to review".
     */
    private val closing = MutableStateFlow(false)
    private var lastPreview: ImportPreview? = null
    private val _done = Channel<Unit>(Channel.BUFFERED)

    /** One-shot: records were written (or the preview was abandoned); leave the screen. */
    val done: Flow<Unit> = _done.receiveAsFlow()

    val state: StateFlow<ImportPreviewUiState> = combine(pendingImport.preview, drafts, saving, closing) { pending, drafts, saving, closing ->
        val preview = if (closing) lastPreview else pending?.also { lastPreview = it }
        if (preview == null) ImportPreviewUiState.Expired else ImportPreviewUiState.Content(preview, drafts, saving || closing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportPreviewUiState.Expired)

    fun setIncome(index: Int, asIncome: Boolean) = drafts.update { it + (index to (it[index] ?: ZeroPriceDraft()).copy(asIncome = asIncome)) }

    fun setFmv(index: Int, text: String) = drafts.update { it + (index to (it[index] ?: ZeroPriceDraft()).copy(fmvText = text, edited = true)) }

    fun confirm() {
        val current = state.value as? ImportPreviewUiState.Content ?: return
        if (!current.canConfirm) return
        saving.value = true
        viewModelScope.launch {
            val resolutions = current.preview.zeroPriceIndices.associateWith { index ->
                (current.drafts[index] ?: ZeroPriceDraft()).toResolution() ?: ZeroPriceResolution.UnknownBasis
            }
            val records = buildPreview.recordsToWrite(current.preview, resolutions)
            transactions.add(records)
            messages.show(UserMessage.Plural(R.plurals.imported_message, records.size))
            finish()
        }
    }

    fun cancel() {
        viewModelScope.launch { finish() }
    }

    private suspend fun finish() {
        closing.value = true
        pendingImport.clear()
        _done.send(Unit)
    }
}
