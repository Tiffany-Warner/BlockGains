package com.tiffles.blockgains.data.importer

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.ImportParseResult
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.model.ZeroPriceResolution
import com.tiffles.blockgains.domain.usecase.BuildImportPreviewUseCase
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import org.junit.Test

/**
 * `buys-sells.csv` is the unified version of the old legacy buys/sells fixture (same rows, unsorted,
 * mixed date formats, `$` on some values, a $0 buy, two bad rows). It keeps the M3/M4 hand-computed
 * year totals that the legacy fixture guarded until the legacy importer was removed in 1.8.0.
 * `converted-from-legacy.csv` is what `tools/legacy_to_unified.py` writes for the old legacy fixture
 * (checked byte for byte by `tools/reference_calc/test_legacy_to_unified.py`).
 */
class BuysSellsFixtureTest {

    private val importer = UnifiedImporter()
    private val previewer = BuildImportPreviewUseCase()
    private val result = parse("buys-sells.csv")

    private fun parse(name: String): ImportParseResult = fixture(name).let { importer.parse(it.text, it.fileName) }

    @Test
    fun mixedDateFormats_unsortedRows_andDollarOnSomeValues() {
        assertThat(result.rows).hasSize(7)
        assertThat(result.rows.map { it.transaction.date }).isInOrder()
        val first = result.rows.first().transaction
        assertThat(first.date).isEqualTo(LocalDate.of(2020, 12, 1))
        assertThat(first.quantity.toPlainString()).isEqualTo("2.123456789")
        val quoted = result.rows.single { it.transaction.date == LocalDate.of(2022, 1, 5) }.transaction
        assertThat(quoted.priceUsd).isEquivalentAccordingToCompareTo(BigDecimal("3012.50"))
        val sell = result.rows.single { it.transaction.date == LocalDate.of(2023, 12, 8) }.transaction
        assertThat(sell.type).isEqualTo(TransactionType.SELL)
        assertThat(sell.priceUsd).isEquivalentAccordingToCompareTo(BigDecimal("2210.41"))
    }

    @Test
    fun everyRowKeepsItsAssetAndWallet() {
        assertThat(result.rows.map { it.transaction.asset }.toSet()).containsExactly("FAKEB")
        assertThat(result.rows.map { it.transaction.wallet }.toSet()).containsExactly("FAKE-WALLET-1")
    }

    @Test
    fun badRows_reportFileAndLine() {
        assertThat(result.issues.map { Triple(it.fileName, it.lineNumber, it.reason) }).containsExactly(
            Triple("buys-sells.csv", 9, InvalidReason.BAD_DATE),
            Triple("buys-sells.csv", 10, InvalidReason.MISSING_FIELD),
        )
    }

    /** R9: a unified $0 BUY with basis_unknown blank asks "unknown basis or income?"; one marked true doesn't. */
    @Test
    fun zeroPriceBuy_isPromptedOnlyWhenBasisUnknownIsBlank() {
        val preview = previewer(result, existing = emptyList())
        assertThat(preview.zeroPriceIndices.map { preview.toAdd[it].transaction.date }).containsExactly(LocalDate.of(2023, 2, 10))

        val marked = parse("unified.csv").rows.single { it.transaction.type == TransactionType.BUY && it.transaction.usdValue?.signum() == 0 }
        assertThat(marked.transaction.basisUnknown).isTrue()
        assertThat(previewer(ImportParseResult(listOf(marked), emptyList()), emptyList()).zeroPriceIndices).isEmpty()
    }

    /** M3/M4 acceptance (was the legacy fixture's): imports and produces the hand-computed year totals. */
    @Test
    fun endToEnd_yearTotals() {
        val preview = previewer(result, existing = emptyList())
        assertThat(preview.zeroPriceIndices).hasSize(1)
        val records = previewer.recordsToWrite(preview, preview.zeroPriceIndices.associateWith { ZeroPriceResolution.UnknownBasis })
            .mapIndexed { i, tx -> tx.copy(id = i + 1L) }

        val calc = TaxCalculator().calculate(records, CalcSettings("FAKE-WALLET-1"))
        assertThat(calc.issues).isEmpty()
        val summarize = SummarizeYearUseCase()
        fun cents(value: BigDecimal) = value.setScale(2, RoundingMode.HALF_UP).toPlainString()

        val y2021 = summarize(calc, 2021, AssetFilter.ALL)
        assertThat(cents(y2021.shortTerm)).isEqualTo("638.95")
        assertThat(cents(y2021.longTerm)).isEqualTo("0.00")
        val y2023 = summarize(calc, 2023, AssetFilter.ALL)
        assertThat(cents(y2023.longTerm)).isEqualTo("2182.17")
        val y2024 = summarize(calc, 2024, AssetFilter.ALL)
        assertThat(cents(y2024.longTerm)).isEqualTo("6595.88")
        assertThat(cents(y2024.net)).isEqualTo("6595.88")
    }

    @Test
    fun convertedLegacyFile_importsWithNoIssues_andMatchesTheFixture() {
        val converted = parse("converted-from-legacy.csv")
        assertThat(converted.issues).isEmpty()
        assertThat(converted.rows.map { key(it.transaction) }).containsExactlyElementsIn(result.rows.map { key(it.transaction) })
    }

    /**
     * Rows stored by the old legacy importer, re-imported from the converter's output, are duplicates.
     * The legacy importer stored BUY/SELL with only price_usd set (no total, fee, or other columns);
     * [legacyStored] rebuilds that. The one exception is the $0 buy: the preview always stored it
     * resolved (basis_unknown or INCOME), and the converter leaves it unresolved, so it isn't a duplicate.
     */
    @Test
    fun rowsStoredByTheLegacyImporter_areDuplicatesOfTheConvertedFile() {
        val converted = parse("converted-from-legacy.csv")
        val unresolved = previewer(converted, legacyStored(resolveZeroPrice = false))
        assertThat(unresolved.toAdd).isEmpty()
        assertThat(unresolved.duplicates).hasSize(7)

        val resolved = previewer(converted, legacyStored(resolveZeroPrice = true))
        assertThat(resolved.duplicates).hasSize(6)
        assertThat(resolved.toAdd.map { it.transaction.date }).containsExactly(LocalDate.of(2023, 2, 10))
    }

    private fun legacyStored(resolveZeroPrice: Boolean): List<Transaction> = result.rows.mapIndexed { i, row ->
        val tx = row.transaction
        Transaction(
            id = i + 1L, type = tx.type, date = tx.date, asset = tx.asset, assetClass = tx.assetClass, wallet = tx.wallet,
            quantity = tx.quantity, priceUsd = tx.priceUsd,
            basisUnknown = resolveZeroPrice && tx.usdValue?.signum() == 0,
        )
    }

    /** Every imported field, numbers scale-insensitive. */
    private fun key(tx: Transaction) = tx.copy(
        quantity = tx.quantity.stripTrailingZeros(),
        priceUsd = tx.priceUsd?.stripTrailingZeros(),
    )
}
