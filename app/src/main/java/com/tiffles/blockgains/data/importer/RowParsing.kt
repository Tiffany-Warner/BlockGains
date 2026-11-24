package com.tiffles.blockgains.data.importer

import com.tiffles.blockgains.domain.calc.TransactionValidator
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.ImportParseResult
import com.tiffles.blockgains.domain.model.ImportedRow
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Thrown by field readers to abandon one row. Confined to the importers: each row is
 * parsed inside [collectRows], which turns it into an InvalidRow issue (R10).
 */
internal class RowRejected(val reason: InvalidReason, val detail: String?) : Exception()

internal fun reject(reason: InvalidReason, detail: String? = null): Nothing = throw RowRejected(reason, detail)

internal fun requireDate(raw: String, column: String): LocalDate =
    ValueParsers.date(raw) ?: reject(InvalidReason.BAD_DATE, column)

internal fun optionalDate(raw: String, column: String): LocalDate? =
    if (raw.isBlank()) null else requireDate(raw, column)

/** Reads one row's numbers and counts those written in scientific notation (for the preview note). */
internal class RowReader {
    var scientificValues: Int = 0
        private set

    fun requireDecimal(raw: String, column: String): BigDecimal {
        val value = ValueParsers.decimal(raw) ?: reject(if (raw.isBlank()) InvalidReason.MISSING_FIELD else InvalidReason.BAD_NUMBER, column)
        if (ValueParsers.isScientific(raw)) scientificValues++
        return value
    }

    fun optionalDecimal(raw: String, column: String): BigDecimal? =
        if (raw.isBlank()) null else requireDecimal(raw, column)
}

/**
 * Parses each record with [parse], validates the result, and collects issues for rows that
 * fail either step. Rows come back sorted by date (files may be in any order).
 */
internal fun collectRows(
    records: List<CsvRecord>,
    fileName: String?,
    parse: RowReader.(CsvRecord) -> Transaction,
): ImportParseResult {
    val rows = mutableListOf<ImportedRow>()
    val issues = mutableListOf<CalcIssue.InvalidRow>()
    for (record in records) {
        try {
            val reader = RowReader()
            val tx = reader.parse(record)
            val reason = TransactionValidator.validate(tx)
            if (reason == null) {
                rows += ImportedRow(tx, record.lineNumber, fileName, reader.scientificValues)
            } else {
                issues += CalcIssue.InvalidRow(reason, lineNumber = record.lineNumber, fileName = fileName, date = tx.date, asset = tx.asset)
            }
        } catch (e: RowRejected) {
            issues += CalcIssue.InvalidRow(e.reason, lineNumber = record.lineNumber, fileName = fileName, detail = e.detail)
        }
    }
    return ImportParseResult(rows.sortedBy { it.transaction.date }, issues)
}

/** File-level failure (e.g. missing header column): no rows, one issue on line 1. */
internal fun missingColumns(fileName: String?, columns: List<String>) = ImportParseResult(
    rows = emptyList(),
    issues = listOf(
        CalcIssue.InvalidRow(InvalidReason.MISSING_COLUMN, lineNumber = 1, fileName = fileName, detail = columns.joinToString()),
    ),
)
