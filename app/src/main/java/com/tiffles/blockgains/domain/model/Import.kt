package com.tiffles.blockgains.domain.model

/** One parsed, valid record and where it came from. */
data class ImportedRow(
    val transaction: Transaction,
    val lineNumber: Int,
    val fileName: String?,
    /** Numbers in this row written in scientific notation (`1.2E-05`); spreadsheets can round these. */
    val scientificValues: Int = 0,
)

/** Output of an importer: usable rows (sorted by date) plus per-row problems. */
data class ImportParseResult(
    val rows: List<ImportedRow>,
    val issues: List<CalcIssue.InvalidRow>,
)

/** How the user resolved a $0 BUY in the import preview (R9). */
sealed interface ZeroPriceResolution {
    /** Intentional $0 basis: sets `basisUnknown`. */
    data object UnknownBasis : ZeroPriceResolution

    /** Actually income: becomes an INCOME record at this per-unit FMV (R8). */
    data class Income(val fmvPerUnit: java.math.BigDecimal) : ZeroPriceResolution
}
