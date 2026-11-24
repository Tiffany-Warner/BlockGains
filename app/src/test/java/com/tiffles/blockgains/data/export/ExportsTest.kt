package com.tiffles.blockgains.data.export

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.data.importer.CsvReader
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat
import com.tiffles.blockgains.data.importer.UnifiedImporter
import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.calc.TxBuilder
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import java.math.RoundingMode
import java.time.Instant
import org.junit.Test

class ExportsTest {

    private val result = TaxCalculator().calculate(ExportFixtures.transactions, CalcSettings("FAKE-WALLET-1"))
    private val exporter = TaxYearCsvExporter()

    @Test
    fun unifiedCsv_roundTripsThroughTheImporter() {
        val csv = UnifiedCsvExporter().export(ExportFixtures.transactions)
        val parsed = UnifiedImporter().parse(csv, "all.csv")

        assertThat(parsed.issues).isEmpty()
        val expected = ExportFixtures.transactions.map { it.copy(id = 0) }.sortedBy { it.date }
        assertThat(parsed.rows.map { it.transaction }).containsExactlyElementsIn(expected)
    }

    /** Same-day TRANSFER/SWAP order is input order, so both round trips must keep it (not re-sort by type or asset). */
    private val sameDayChain = TxBuilder().apply {
        transfer("2025-03-10", qty = "4", from = "FAKE-WALLET-2", to = "FAKE-EXCHANGE-1", asset = "FAKEC")
        swap("2025-03-10", qty = "1", asset = "FAKEB", toAsset = "FAKEC", toQty = "4", value = "200", wallet = "FAKE-WALLET-2")
        transfer("2025-03-10", qty = "1", from = "FAKE-WALLET-2", to = "FAKE-EXCHANGE-1", asset = "FAKEB")
    }.transactions.map { it.copy(id = 0) }

    @Test
    fun unifiedCsv_keepsSameDayOrder() {
        val parsed = UnifiedImporter().parse(UnifiedCsvExporter().export(sameDayChain), "all.csv")
        assertThat(parsed.rows.map { it.transaction }).containsExactlyElementsIn(sameDayChain).inOrder()
    }

    @Test
    fun jsonBackup_keepsSameDayOrder() {
        val codec = BackupCodec()
        val text = codec.encode(sameDayChain, AppSettings(defaultWallet = "FAKE-WALLET-2"), Instant.parse("2026-11-21T10:00:00Z"))
        assertThat(codec.decode(text).getOrThrow().transactions).containsExactlyElementsIn(sameDayChain).inOrder()
    }

    @Test
    fun template_importsAsEmptyButValid() {
        val parsed = UnifiedImporter().parse(UnifiedCsvExporter().template(), "template.csv")
        assertThat(parsed.rows).isEmpty()
        assertThat(parsed.issues).isEmpty()
    }

    @Test
    fun template_saysTotalsAreBeforeFees_andKeepsTheExportHeader() {
        val lines = UnifiedCsvExporter().template().lines()
        assertThat(lines.first()).isEqualTo(UnifiedCsvFormat.HEADER.joinToString(","))
        val comments = lines.filter { it.startsWith(UnifiedCsvFormat.COMMENT_PREFIX) }
        assertThat(comments.any { "BEFORE fees" in it }).isTrue()
        assertThat(comments.any { "fee_usd" in it && "R7" in it }).isTrue()
        assertThat(comments.any { it.startsWith("# TRANSFER: quantity = amount received") && "R13" in it }).isTrue()
    }

    @Test
    fun jsonBackup_roundTripsRecordsAndSettings() {
        val codec = BackupCodec()
        val settings = AppSettings(defaultWallet = "FAKE-EXCHANGE-1", walletLabels = setOf("FAKE-EXCHANGE-1", "FAKE-WALLET-2"), themeMode = ThemeMode.DARK, dynamicColor = false)
        val text = codec.encode(ExportFixtures.transactions, settings, Instant.parse("2026-11-21T10:00:00Z"))

        val backup = codec.decode(text).getOrThrow()
        assertThat(backup.settings).isEqualTo(settings)
        assertThat(backup.transactions).containsExactlyElementsIn(ExportFixtures.transactions.map { it.copy(id = 0) }).inOrder()
        assertThat(backup.exportedAt).isEqualTo(Instant.parse("2026-11-21T10:00:00Z"))
        assertThat(text).doesNotContainMatch(""""\d+(\.\d+)?[eE][-+]?\d+"""") // never scientific notation
        assertThat(text).contains("\"format\": \"blockgains-backup\"")
        assertThat(text).contains("\"version\": 2")
    }

    @Test
    fun jsonBackup_version1_stillRestores_withoutFeeColumns() {
        val v1 = """{"format":"blockgains-backup","version":1,"exportedAt":"2026-01-01T00:00:00Z",
            "settings":{"defaultWallet":"FAKE-WALLET-1","walletLabels":[],"themeMode":"SYSTEM","dynamicColor":false},
            "transactions":[{"type":"TRANSFER","date":"2025-03-12","asset":"FAKEB","assetClass":"CRYPTO","wallet":"FAKE-WALLET-1",
            "quantity":"0.5","feeUsd":null,"toWallet":"FAKE-EXCHANGE-1","basisUnknown":false}]}"""
        val tx = BackupCodec().decode(v1).getOrThrow().transactions.single()
        assertThat(tx.toWallet).isEqualTo("FAKE-EXCHANGE-1")
        assertThat(tx.feeAsset).isNull()
        assertThat(tx.feeQuantity).isNull()
    }

    @Test
    fun transferFee_isMarkedInTheLotExports() {
        val records = TxBuilder().apply {
            opening("FAKEB", "FAKE-WALLET-1", qty = "1", acquired = "2024-01-10", basis = "2000")
            transfer("2025-04-01", qty = "0.99", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEB", feeQty = "0.01", feeUsd = "30.00")
        }.transactions
        val data = TaxYearData.from(TaxCalculator().calculate(records, CalcSettings("FAKE-WALLET-1")), records, 2025, AssetFilter.ALL)

        assertThat(exporter.form8949(data).lines()).contains("0.01 FAKEB (transfer fee),01/10/2024,04/01/2025,30.00,20.00,10.00")
        val events = CsvReader.read(exporter.taxableEvents(data)).map { it.fields }
        assertThat(events.first().last()).isEqualTo("transfer_fee")
        assertThat(events[1].last()).isEqualTo("true")
    }

    @Test
    fun jsonBackup_rejectsOtherFiles() {
        val codec = BackupCodec()
        assertThat(codec.decode("not json").isFailure).isTrue()
        assertThat(codec.decode("""{"format":"other","version":1,"exportedAt":"2026-01-01T00:00:00Z","settings":{"defaultWallet":"W","walletLabels":[],"themeMode":"SYSTEM","dynamicColor":true},"transactions":[]}""").isFailure).isTrue()
        assertThat(codec.decode("""{"format":"blockgains-backup","version":99,"exportedAt":"2026-01-01T00:00:00Z","settings":{"defaultWallet":"W","walletLabels":[],"themeMode":"SYSTEM","dynamicColor":true},"transactions":[]}""").isFailure).isTrue()
    }

    @Test
    fun form8949_totalsEqualTheSummary_forEveryFilter() {
        val summarize = SummarizeYearUseCase()
        for (filter in AssetFilter.entries) {
            val data = TaxYearData.from(result, ExportFixtures.transactions, 2024, filter)
            val summary = summarize(result, 2024, filter)
            val csv = CsvReader.read(exporter.form8949(data)).map { it.fields }
            val totals = csv.filter { it.first() == "Totals" }.map { it[5] }

            assertThat(totals).containsExactly(cents(summary.shortTerm), cents(summary.longTerm)).inOrder()
        }
    }

    @Test
    fun form8949_hasOneRowPerLot_andSplitsMixedDisposals() {
        val data = TaxYearData.from(result, ExportFixtures.transactions, 2024, AssetFilter.ALL)
        val csv = CsvReader.read(exporter.form8949(data)).map { it.fields }
        // Part I: title row, column header, one row per short-term lot, then "Totals".
        val firstLot = csv.indexOf(listOf("Part I - Short-term")) + 2
        val totals = csv.indexOfFirst { it.first() == "Totals" }

        assertThat(totals - firstLot).isEqualTo(data.lotRows.count { it.lot.term == HoldingTerm.SHORT })
        val mixed = data.events.single { it.term == null }
        assertThat(mixed.lots.map { it.term }).containsExactly(HoldingTerm.LONG, HoldingTerm.SHORT)
        assertThat(csv).contains(listOf("Part I - Short-term"))
    }

    @Test
    fun taxableEventsCsv_hasOneRowPerLot() {
        val data = TaxYearData.from(result, ExportFixtures.transactions, 2024, AssetFilter.ALL)
        val rows = CsvReader.read(exporter.taxableEvents(data))
        assertThat(rows).hasSize(1 + data.lotRows.size)
        assertThat(rows.first().fields.first()).isEqualTo("event_id")
    }

    @Test
    fun unmatchedUnits_showUnknownAcquiredDate_inBothLotExports() {
        val records = TxBuilder().apply {
            buy("2024-01-10", qty = "1", price = "100", asset = "FAKEA")
            sell("2024-03-01", qty = "1.5", price = "200", asset = "FAKEA") // 0.5 without a lot
        }.transactions
        val data = TaxYearData.from(TaxCalculator().calculate(records, CalcSettings("FAKE-WALLET-1")), records, 2024, AssetFilter.ALL)
        val exporter = TaxYearCsvExporter()

        val events = exporter.taxableEvents(data).lines().drop(1).filter { it.isNotBlank() }
        assertThat(events.map { it.split(",")[2] }).containsExactly("2024-01-10", "Unknown").inOrder()
        val form = exporter.form8949(data).lines()
        assertThat(form).contains("0.5 FAKEA,Unknown,03/01/2024,100.00,0.00,100.00")
        assertThat(form).contains("1 FAKEA,01/10/2024,03/01/2024,200.00,100.00,100.00")
    }

    @Test
    fun incomeCsv_totalsTheYear() {
        val data = TaxYearData.from(result, ExportFixtures.transactions, 2024, AssetFilter.ALL)
        val rows = CsvReader.read(exporter.income(data)).map { it.fields }
        assertThat(rows.last()[6]).isEqualTo(cents(data.ordinaryIncome))
        assertThat(rows[1][5]).isEqualTo("3020.33") // FMV per unit
    }

    @Test
    fun pdfPagination_coversEveryRowOnce() {
        val pages = PdfPagination.pages(rowCount = 120, firstPageRows = PdfReportExporter.FIRST_PAGE_ROWS, otherPageRows = PdfReportExporter.OTHER_PAGE_ROWS)
        assertThat(pages.size).isAtLeast(3)
        assertThat(pages.flatMap { it.toList() }).containsExactlyElementsIn(0 until 120).inOrder()
        assertThat(PdfPagination.pages(0, 10, 20)).containsExactly(0 until 0)
    }

    private fun cents(value: java.math.BigDecimal) = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
