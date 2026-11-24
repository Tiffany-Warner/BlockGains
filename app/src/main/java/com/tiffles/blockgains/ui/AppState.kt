package com.tiffles.blockgains.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.usecase.ImportPreview
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tax year and asset-class filter shared by Summary, Transactions and exports (ui-guidelines → Navigation). */
@Singleton
class SharedSelection @Inject constructor() {
    private val _year = MutableStateFlow<Int?>(null)

    /** Explicit choice; null means "use the default year" (ui-guidelines → Summary). */
    val year: StateFlow<Int?> = _year.asStateFlow()

    private val _filter = MutableStateFlow(AssetFilter.ALL)
    val filter: StateFlow<AssetFilter> = _filter.asStateFlow()

    fun selectYear(year: Int) {
        _year.value = year
    }

    fun selectFilter(filter: AssetFilter) {
        _filter.value = filter
    }

    private val _walletRenames = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)

    /** (old label, new label) after a wallet rename, so a screen's wallet filter can follow it. */
    val walletRenames: SharedFlow<Pair<String, String>> = _walletRenames.asSharedFlow()

    fun walletRenamed(from: String, to: String) {
        _walletRenames.tryEmit(from to to)
    }

    /** Back to the defaults (after deleting data, the chosen year may no longer exist). */
    fun reset() {
        _year.value = null
        _filter.value = AssetFilter.ALL
    }
}

/** A one-shot message for the app snackbar. Resolved to text in the UI, so ViewModels need no Context. */
sealed interface UserMessage {
    data class Text(@StringRes val res: Int, val args: List<Any> = emptyList()) : UserMessage
    data class Plural(@PluralsRes val res: Int, val count: Int) : UserMessage
}

/** App-wide snackbar events (SharedFlow: one-shot, not state). */
@Singleton
class UserMessages @Inject constructor() {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun show(message: UserMessage) {
        _messages.tryEmit(message)
    }
}

/** Hands a parsed import from the Import screen to the Preview screen without passing data through navigation. */
@Singleton
class PendingImport @Inject constructor() {
    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview: StateFlow<ImportPreview?> = _preview.asStateFlow()

    fun set(preview: ImportPreview) {
        _preview.value = preview
    }

    fun clear() {
        _preview.value = null
    }
}
