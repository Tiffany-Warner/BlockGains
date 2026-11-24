package com.tiffles.blockgains.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tiffles.blockgains.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Files in and out only through the Storage Access Framework (URIs from `OpenDocument` /
 * `CreateDocument`), so the app needs no storage permissions.
 */
interface DocumentFiles {
    suspend fun readText(uri: Uri): Result<NamedDocument>

    suspend fun write(uri: Uri, writer: (OutputStream) -> Unit): Result<Unit>

    /** The document's display name, if the provider gives one. */
    suspend fun displayName(uri: Uri): String?
}

/** Contents of a picked document plus its display name (for issue messages). */
data class NamedDocument(val name: String?, val text: String)

@Singleton
class SafDocumentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DocumentFiles {

    override suspend fun readText(uri: Uri): Result<NamedDocument> = withContext(io) {
        runCatching {
            val resolver = context.contentResolver
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
            if (size != null && size > MAX_BYTES) throw IOException("File is larger than ${MAX_BYTES / 1_000_000} MB")
            val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IOException("Can't open file")
            NamedDocument(queryName(uri), text)
        }
    }

    override suspend fun write(uri: Uri, writer: (OutputStream) -> Unit): Result<Unit> = withContext(io) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open file")
            stream.use(writer)
        }
    }

    override suspend fun displayName(uri: Uri): String? = withContext(io) { runCatching { queryName(uri) }.getOrNull() }

    private fun queryName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    private companion object {
        /** Guard against picking something that isn't a transaction file. */
        const val MAX_BYTES = 20_000_000L
    }
}
