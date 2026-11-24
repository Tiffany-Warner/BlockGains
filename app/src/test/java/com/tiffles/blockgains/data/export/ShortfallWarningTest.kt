package com.tiffles.blockgains.data.export

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.calc.TxBuilder
import com.tiffles.blockgains.domain.calc.d
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import org.junit.Test

/** Export warnings for unresolved shortfalls (tax-rules → Open questions, partial shortfall: option A + warnings). */
class ShortfallWarningTest {

    // Every disposal/transfer below has nothing to match, so each one is an InsufficientLots issue.
    private val records = TxBuilder().apply {
        sell("2023-06-01", qty = "1", price = "100", asset = "FAKEA") // earlier year
        sell("2024-03-01", qty = "2", price = "40", asset = "FAKEE", assetClass = AssetClass.STOCK)
        sell("2024-12-31", qty = "1", price = "100", asset = "FAKEA") // last day of the year
        sell("2025-01-01", qty = "1", price = "100", asset = "FAKEA") // next year
        transfer("2025-02-01", qty = "3", from = "FAKE-WALLET-2", to = "FAKE-EXCHANGE-1", asset = "FAKEB")
    }.transactions
    private val result = TaxCalculator().calculate(records, CalcSettings("FAKE-WALLET-1"))

    private fun dates(year: Int, filter: AssetFilter) =
        TaxYearData.shortfallsThrough(result, records, year, filter).map { it.date.toString() }

    @Test
    fun countsIssuesUpToTheEndOfTheYear_includingEarlierYears() {
        assertThat(dates(2024, AssetFilter.ALL)).containsExactly("2023-06-01", "2024-03-01", "2024-12-31").inOrder()
        assertThat(dates(2022, AssetFilter.ALL)).isEmpty()
    }

    @Test
    fun followsTheAssetFilter() {
        assertThat(dates(2024, AssetFilter.CRYPTO)).containsExactly("2023-06-01", "2024-12-31").inOrder()
        assertThat(dates(2024, AssetFilter.STOCK)).containsExactly("2024-03-01")
    }

    @Test
    fun transferShortfallsCount() {
        val issues = TaxYearData.shortfallsThrough(result, records, 2025, AssetFilter.CRYPTO)
        assertThat(issues.map { it.type }).contains(TransactionType.TRANSFER)
        assertThat(issues).hasSize(4)
    }

    /** R13: a fee-only shortfall warns like any other (the Export card counts this same list; the PDF banner prints it). */
    @Test
    fun transferFeeShortfall_warnsOnExportCardAndPdfBanner() {
        val feeRecords = TxBuilder().apply {
            opening("FAKEUSD", "FAKE-WALLET-2", qty = "50", acquired = "2024-06-01", basis = "50")
            transfer(
                "2025-03-01", qty = "50", from = "FAKE-WALLET-2", to = "FAKE-EXCHANGE-1", asset = "FAKEUSD",
                feeQty = "0.01", feeUsd = "20.00", feeAsset = "FAKEB",
            )
        }.transactions
        val feeResult = TaxCalculator().calculate(feeRecords, CalcSettings("FAKE-WALLET-1"))

        val shortfalls = TaxYearData.shortfallsThrough(feeResult, feeRecords, 2025, AssetFilter.ALL)
        assertThat(shortfalls.map { it.asset to it.networkFee }).containsExactly("FAKEB" to true)
        assertThat(TaxYearData.from(feeResult, feeRecords, 2025, AssetFilter.CRYPTO).shortfalls).hasSize(1)

        val lines = PdfShortfallBanner.lines(shortfalls, 2025)
        assertThat(lines.first()).isEqualTo("Warning: 1 unresolved shortfall in your records up to 2025.")
        assertThat(lines).contains("2025-03-01  TRANSFER fee  FAKEB  FAKE-WALLET-2  short by 0.01")
    }

    @Test
    fun issueWithUnknownRow_warnsUnderEveryFilter() {
        assertThat(TaxYearData.shortfallsThrough(result, emptyList(), 2024, AssetFilter.STOCK)).hasSize(3)
    }

    @Test
    fun yearData_carriesTheShortfalls_butCsvsDoNotChange() {
        val data = TaxYearData.from(result, records, 2024, AssetFilter.ALL)
        assertThat(data.shortfalls).hasSize(3)
        val exporter = TaxYearCsvExporter()
        val without = data.copy(shortfalls = emptyList())
        assertThat(exporter.form8949(data)).isEqualTo(exporter.form8949(without))
        assertThat(exporter.taxableEvents(data)).isEqualTo(exporter.taxableEvents(without))
        assertThat(exporter.income(data)).isEqualTo(exporter.income(without))
    }

    /** A null [wallet] means a pre-2025 (pooled) shortfall from FAKE-WALLET-1. */
    private fun issue(date: String, wallet: String?, requested: String = "2", available: String = "0.5") = CalcIssue.InsufficientLots(
        transactionId = 1, type = TransactionType.SELL, date = d(date), asset = "FAKEA", wallet = wallet ?: "FAKE-WALLET-1", pooled = wallet == null,
        requested = BigDecimal(requested), available = BigDecimal(available),
    )

    @Test
    fun banner_isEmptyWithoutShortfalls() {
        assertThat(PdfShortfallBanner.lines(emptyList(), 2024)).isEmpty()
        assertThat(PdfReportExporter.firstPageRows(0)).isEqualTo(PdfReportExporter.FIRST_PAGE_ROWS)
    }

    @Test
    fun banner_namesEachShortfall() {
        val lines = PdfShortfallBanner.lines(listOf(issue("2024-12-31", wallet = null), issue("2025-02-01", wallet = "FAKE-EXCHANGE-1")), 2025)
        assertThat(lines.first()).isEqualTo("Warning: 2 unresolved shortfalls in your records up to 2025.")
        assertThat(lines).contains("2024-12-31  SELL  FAKEA  FAKE-WALLET-1 (all wallets pooled)  short by 1.5")
        assertThat(lines).contains("2025-02-01  SELL  FAKEA  FAKE-EXCHANGE-1  short by 1.5")
        assertThat(PdfShortfallBanner.lines(listOf(issue("2024-01-01", null)), 2024).first())
            .isEqualTo("Warning: 1 unresolved shortfall in your records up to 2024.")
    }

    @Test
    fun banner_wrapsLongLines_insteadOfCuttingThem() {
        val text = "Some sales, swaps, transfers or adjustments used more units than were available."
        val wrapped = PdfShortfallBanner.wrap(text, maxWidth = 30f) { it.length.toFloat() }
        assertThat(wrapped.joinToString(" ")).isEqualTo(text)
        assertThat(wrapped.size).isGreaterThan(1)
        assertThat(wrapped.all { it.length <= 30 }).isTrue()
        // Double spaces between fields survive when they don't fall at a line break.
        assertThat(PdfShortfallBanner.wrap("2024-12-31  SELL  FAKEA", 100f) { it.length.toFloat() }).containsExactly("2024-12-31  SELL  FAKEA")
    }

    @Test
    fun pdfMoney_signsGainsAndUsesTheTrueMinus() {
        assertThat(PdfReportExporter.money(BigDecimal("1234.5"), signed = true)).isEqualTo("+$1,234.50")
        assertThat(PdfReportExporter.money(BigDecimal("-1.5"), signed = true)).isEqualTo("−$1.50")
        assertThat(PdfReportExporter.money(BigDecimal("-0.001"), signed = true)).isEqualTo("$0.00")
        assertThat(PdfReportExporter.money(BigDecimal("0"), signed = true)).isEqualTo("$0.00")
        assertThat(PdfReportExporter.money(BigDecimal("250"))).isEqualTo("$250.00")
    }

    @Test
    fun banner_capsDetailLines() {
        val many = (1..7).map { issue("2024-01-0$it", wallet = null) }
        val lines = PdfShortfallBanner.lines(many, 2024)
        assertThat(lines).hasSize(2 + PdfShortfallBanner.MAX_DETAIL_LINES + 1)
        assertThat(lines.last()).isEqualTo("…and 2 more. See Issues in the app.")
    }

    @Test
    fun banner_takesRowsFromPageOne_andPaginationStillCoversEveryRow() {
        val bannerLines = PdfShortfallBanner.lines((1..7).map { issue("2024-01-0$it", null) }, 2024).size
        val firstRows = PdfReportExporter.firstPageRows(bannerLines)
        assertThat(firstRows).isLessThan(PdfReportExporter.FIRST_PAGE_ROWS)
        assertThat(firstRows).isAtMost(PdfReportExporter.firstPageRows(3))

        val pages = PdfPagination.pages(rowCount = 120, firstPageRows = firstRows, otherPageRows = PdfReportExporter.OTHER_PAGE_ROWS)
        assertThat(pages.first().count()).isEqualTo(firstRows)
        assertThat(pages.flatMap { it.toList() }).containsExactlyElementsIn(0 until 120).inOrder()
    }
}
