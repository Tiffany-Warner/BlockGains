package com.tiffles.blockgains.ui.importexport

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.R
import com.tiffles.blockgains.data.export.UnifiedCsvExporter
import com.tiffles.blockgains.data.files.DocumentFiles
import com.tiffles.blockgains.data.importer.UnifiedImporter
import com.tiffles.blockgains.domain.model.ImportParseResult
import com.tiffles.blockgains.domain.repository.TransactionRepository
import com.tiffles.blockgains.domain.usecase.BuildImportPreviewUseCase
import com.tiffles.blockgains.ui.PendingImport
import com.tiffles.blockgains.ui.UserMessage
import com.tiffles.blockgains.ui.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A document the user picked through SAF. */
data class PickedFile(val uri: Uri, val name: String?)

/** Import is one unified CSV (import-format-spec → Unified format): pick a file → preview. */
data class ImportUiState(
    val file: PickedFile? = null,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
) {
    val canPreview: Boolean get() = !busy && file != null
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val documents: DocumentFiles,
    private val importer: UnifiedImporter,
    private val buildPreview: BuildImportPreviewUseCase,
    private val transactions: TransactionRepository,
    private val exporter: UnifiedCsvExporter,
    private val pendingImport: PendingImport,
    private val messages: UserMessages,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val _openPreview = Channel<Unit>(Channel.BUFFERED)

    /** One-shot: the preview is ready. */
    val openPreview: Flow<Unit> = _openPreview.receiveAsFlow()

    fun pickFile(uri: Uri) {
        viewModelScope.launch {
            val file = PickedFile(uri, documents.displayName(uri))
            _state.update { it.copy(file = file, error = null) }
        }
    }

    fun preview() {
        val file = _state.value.file
        if (!_state.value.canPreview || file == null) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val parsed = parse(file)
            if (parsed == null) {
                _state.update { it.copy(busy = false, error = R.string.import_read_error) }
                return@launch
            }
            pendingImport.set(buildPreview(parsed, transactions.getAll()))
            _state.update { it.copy(busy = false) }
            _openPreview.send(Unit)
        }
    }

    fun saveTemplate(uri: Uri) {
        viewModelScope.launch {
            val text = exporter.template()
            documents.write(uri) { it.write(text.toByteArray(Charsets.UTF_8)) }
                .onSuccess { messages.show(UserMessage.Text(R.string.template_saved)) }
                .onFailure { messages.show(UserMessage.Text(R.string.file_save_error)) }
        }
    }

    /** Null if the file can't be read. */
    private suspend fun parse(file: PickedFile): ImportParseResult? {
        val doc = documents.readText(file.uri).getOrNull() ?: return null
        return importer.parse(doc.text, doc.name ?: file.name)
    }
}
