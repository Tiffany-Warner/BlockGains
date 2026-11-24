package com.tiffles.blockgains.data.export

import com.tiffles.blockgains.data.importer.UnifiedCsvFormat
import com.tiffles.blockgains.domain.model.Transaction
import javax.inject.Inject

/** "All transactions" export and the blank template, both in the unified format (round-trippable). */
class UnifiedCsvExporter @Inject constructor() {

    fun export(transactions: List<Transaction>): String =
        CsvWriter.write(listOf(UnifiedCsvFormat.HEADER) + transactions.map(UnifiedCsvFormat::fields))

    fun template(): String = CsvWriter.write(listOf(UnifiedCsvFormat.HEADER), UnifiedCsvFormat.TEMPLATE_EXAMPLES)
}
