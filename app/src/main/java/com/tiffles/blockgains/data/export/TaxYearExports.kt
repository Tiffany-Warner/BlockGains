package com.tiffles.blockgains.data.export

import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.IncomeEvent
import com.tiffles.blockgains.domain.model.LotMatch
import com.tiffles.blockgains.domain.model.TaxableEvent
import com.tiffles.blockgains.domain.model.Transaction
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Export text formatting: money rounded HALF_UP to cents (tax-rules → Rounding), quantities at full precision. */
internal object ExportFormat {
    private val US_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    fun money(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
    fun quantity(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
    fun usDate(date: java.time.LocalDate): String = US_DATE.format(date)

    /** Acquired date of units sold without a matching lot (tax-rules R3). */
    const val UNKNOWN_DATE = "Unknown"

    /** Form 8949 column (a): units + symbol, marked when the units paid a transfer's network fee (R13). */
    fun description(event: TaxableEvent, quantity: BigDecimal): String =
        "${quantity(quantity)} ${event.asset}" + if (event.isTransferFee) " (transfer fee)" else ""
}

/** One disposal-lot pair, the unit every per-lot export works in. */
data class LotRow(val event: TaxableEvent, val lot: LotMatch)

/**
 * A year's slice of the calculation, filtered like the Summary (so totals match what's on screen).
 * [shortfalls] feed the export warnings (tax-rules → Open questions, partial shortfall); the CSVs ignore them.
 */
data class TaxYearData(
    val year: Int,
    val filter: AssetFilter,
    val events: List<TaxableEvent>,
    val income: List<IncomeEvent>,
    val shortfalls: List<CalcIssue.InsufficientLots> = emptyList(),
) {
    val lotRows: List<LotRow> get() = events.flatMap { event -> event.lots.map { LotRow(event, it) } }
    val shortTerm: BigDecimal get() = events.sumOf { it.shortTermGain }
    val longTerm: BigDecimal get() = events.sumOf { it.longTermGain }
    val net: BigDecimal get() = shortTerm + longTerm
    val ordinaryIncome: BigDecimal get() = income.sumOf { it.valueUsd }

    companion object {
        fun from(result: CalcResult, transactions: List<Transaction>, year: Int, filter: AssetFilter) = TaxYearData(
            year = year,
            filter = filter,
            events = result.taxableEvents.filter { it.date.year == year && filter.matches(it.assetClass) }.sortedBy { it.date },
            income = result.incomeEvents.filter { it.date.year == year && filter.matches(it.assetClass) }.sortedBy { it.date },
            shortfalls = shortfallsThrough(result, transactions, year, filter),
        )

        /**
         * Unresolved `InsufficientLots` issues dated on or before Dec 31 of [year], for assets in [filter].
         * Earlier years count because a shortfall (e.g. a transfer) changes the basis carried into [year].
         * Issues are recalculated with every change, so every one present is unresolved.
         */
        fun shortfallsThrough(result: CalcResult, transactions: List<Transaction>, year: Int, filter: AssetFilter): List<CalcIssue.InsufficientLots> {
            val classById = transactions.associate { it.id to it.assetClass }
            return result.issues.filterIsInstance<CalcIssue.InsufficientLots>()
                // An unknown row can't be placed in a class, so it warns under every filter (the safe side).
                .filter { it.date.year <= year && (classById[it.transactionId]?.let(filter::matches) ?: true) }
                .sortedBy { it.date }
        }
    }
}

/** The three tax-year CSVs in ui-guidelines → Export. Pure: text in, text out. */
class TaxYearCsvExporter @Inject constructor() {

    /** Export 1: one row per matched lot, so mixed-term disposals stay accurate. */
    fun taxableEvents(data: TaxYearData): String {
        val header = listOf(
            "event_id", "date_sold", "date_acquired", "asset", "asset_class", "wallet", "quantity",
            "proceeds_usd", "cost_basis_usd", "fee_usd", "gain_loss_usd", "term", "matching", "basis_unknown", "received_asset",
            "transfer_fee",
        )
        val rows = data.lotRows.map { (event, lot) ->
            listOf(
                event.transactionId.toString(), event.date.toString(), lot.acquiredDate?.toString() ?: ExportFormat.UNKNOWN_DATE, event.asset,
                event.assetClass.name, event.wallet, ExportFormat.quantity(lot.quantity), ExportFormat.money(lot.proceeds),
                ExportFormat.money(lot.basis), ExportFormat.money(lot.fee), ExportFormat.money(lot.gain), lot.term.name,
                event.scope.name, if (lot.basisUnknown) "true" else "", event.receivedAsset.orEmpty(),
                if (event.isTransferFee) "true" else "",
            )
        }
        return CsvWriter.write(listOf(header) + rows)
    }

    /**
     * Export 3: Form 8949-style. Part I short-term, Part II long-term; one row per lot (multi-lot
     * disposals become several rows). Each part's total row is computed from unrounded values and
     * then rounded, so it equals the Summary; individual rows may differ from it by rounding cents.
     */
    fun form8949(data: TaxYearData): String {
        val columns = listOf(
            "(a) Description of property", "(b) Date acquired", "(c) Date sold or disposed of",
            "(d) Proceeds", "(e) Cost or other basis", "(h) Gain or (loss)",
        )
        val rows = mutableListOf<List<String>>()
        listOf(HoldingTerm.SHORT to "Part I - Short-term", HoldingTerm.LONG to "Part II - Long-term").forEach { (term, title) ->
            val lots = data.lotRows.filter { it.lot.term == term }
            rows += listOf(title)
            rows += columns
            lots.forEach { (event, lot) ->
                rows += listOf(
                    ExportFormat.description(event, lot.quantity), lot.acquiredDate?.let(ExportFormat::usDate) ?: ExportFormat.UNKNOWN_DATE, ExportFormat.usDate(event.date),
                    ExportFormat.money(lot.proceeds), ExportFormat.money(lot.basis), ExportFormat.money(lot.gain),
                )
            }
            val totals = form8949Totals(data, term)
            rows += listOf("Totals", "", "", ExportFormat.money(totals.proceeds), ExportFormat.money(totals.basis), ExportFormat.money(totals.gain))
            rows += listOf("")
        }
        return CsvWriter.write(rows)
    }

    /** Export 4: ordinary income (R8), with a total row. */
    fun income(data: TaxYearData): String {
        val header = listOf("date", "asset", "asset_class", "wallet", "quantity", "fmv_per_unit_usd", "income_usd", "note")
        val rows = data.income.map {
            listOf(
                it.date.toString(), it.asset, it.assetClass.name, it.wallet, ExportFormat.quantity(it.quantity),
                ExportFormat.money(it.valueUsd.divide(it.quantity, MathContext.DECIMAL128)), ExportFormat.money(it.valueUsd), it.note.orEmpty(),
            )
        }
        val total = listOf("Total", "", "", "", "", "", ExportFormat.money(data.ordinaryIncome), "")
        return CsvWriter.write(listOf(header) + rows + listOf(total))
    }

    data class PartTotals(val proceeds: BigDecimal, val basis: BigDecimal, val gain: BigDecimal)

    fun form8949Totals(data: TaxYearData, term: HoldingTerm): PartTotals {
        val lots = data.lotRows.filter { it.lot.term == term }.map { it.lot }
        return PartTotals(lots.sumOf { it.proceeds }, lots.sumOf { it.basis }, lots.sumOf { it.gain })
    }
}
