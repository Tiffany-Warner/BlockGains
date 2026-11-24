package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tiffles.blockgains.data.importer.UnifiedImporter
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Differential test (`tools/reference_calc/README.md`): the calculator against an independent Python
 * reference on seeded, made-up datasets. Each pair in `fixtures/differential` is a unified CSV plus the
 * reference's `.expected.json`; regenerate both with `gen.py`, never edit them by hand.
 *
 * Every mismatch across all fixtures is collected into one failure message, with the first event that
 * disagrees. A mismatch doesn't mean the app is wrong: find the rule it comes from in `tax-rules.md`.
 */
class DifferentialFixturesTest {

    private val calculator = TaxCalculator(FifoStrategy)
    private val summarize = SummarizeYearUseCase()

    @Test
    fun calculatorMatchesReference_onEveryFixture() {
        val dir = File(checkNotNull(javaClass.getResource("/fixtures/differential")) { "Missing differential fixtures" }.toURI())
        val csvs = dir.listFiles { file -> file.extension == "csv" }.orEmpty().sortedBy { it.name }
        assertThat(csvs.size).isAtLeast(MIN_FIXTURES)

        val mismatches = csvs.flatMap { csv -> compare(csv).map { "${csv.name}: $it" } }

        assertWithMessage("Differences from the reference:\n" + mismatches.joinToString("\n")).that(mismatches).isEmpty()
    }

    private fun compare(csv: File): List<String> {
        val expected = Json.parseToJsonElement(File(csv.parentFile, csv.nameWithoutExtension + ".expected.json").readText()).jsonObject
        val parsed = UnifiedImporter().parse(csv.readText(), csv.name)
        // The importer keeps file order within a date, like Room ids after an import (R3 same-day order).
        val transactions = parsed.rows.mapIndexed { i, row -> row.transaction.copy(id = i + 1L) }
        val lineById = parsed.rows.mapIndexed { i, row -> i + 1L to row.lineNumber }.toMap()
        val result = calculator.calculate(transactions, CalcSettings(defaultWallet = expected.obj("settings").text("default_wallet")))

        val differences = yearDifferences(expected.obj("years"), result).toMutableList()
        val actualIssues = (parsed.issues + result.issues).groupingBy { it::class.simpleName.orEmpty() }.eachCount()
        val expectedIssues = expected.getValue("issues").jsonArray.groupingBy { it.jsonObject.text("kind") }.eachCount()
        if (actualIssues != expectedIssues) differences += "issues: expected $expectedIssues, app $actualIssues"

        if (differences.isNotEmpty()) firstEventDifference(expected, result, lineById)?.let { differences += it }
        return differences
    }

    private fun yearDifferences(years: JsonObject, result: CalcResult): List<String> {
        val activeYears = years.keys.map(String::toInt) + result.taxableEvents.map { it.date.year } + result.incomeEvents.map { it.date.year }
        return activeYears.toSortedSet().flatMap { year ->
            val expected = years[year.toString()]?.jsonObject
            val summary = summarize(result, year, AssetFilter.ALL)
            val lots = result.taxableEvents.filter { it.date.year == year }.flatMap { it.lots }
            listOfNotNull(
                moneyDifference("$year short-term", expected?.obj("short_term")?.text("gain"), summary.shortTerm),
                moneyDifference("$year long-term", expected?.obj("long_term")?.text("gain"), summary.longTerm),
                moneyDifference("$year net", expected?.text("net_gain"), summary.net),
                moneyDifference("$year income", expected?.text("ordinary_income"), summary.ordinaryIncome),
                countDifference("$year short-term lot count", expected?.obj("short_term")?.int("count"), lots.count { it.term == HoldingTerm.SHORT }),
                countDifference("$year long-term lot count", expected?.obj("long_term")?.int("count"), lots.count { it.term == HoldingTerm.LONG }),
            )
        }
    }

    /** First lot-level event (disposal line, quantity, acquired date, term, proceeds, basis) that differs. */
    private fun firstEventDifference(expected: JsonObject, result: CalcResult, lineById: Map<Long, Int>): String? {
        val reference = expected.getValue("events").jsonArray.map { element ->
            val e = element.jsonObject
            listOf(e.text("line"), e.text("quantity"), e.textOrNull("acquired") ?: "Unknown", e.text("term"), e.text("proceeds"), e.text("basis"))
        }
        val app = result.taxableEvents.flatMap { event ->
            event.lots.map { lot ->
                listOf(
                    lineById.getValue(event.transactionId).toString(), quantity(lot.quantity), lot.acquiredDate?.toString() ?: "Unknown",
                    if (lot.term == HoldingTerm.SHORT) "ST" else "LT", cents(lot.proceeds), cents(lot.basis),
                )
            }
        }
        val index = (0 until maxOf(reference.size, app.size)).firstOrNull { reference.getOrNull(it) != app.getOrNull(it) } ?: return null
        return "first differing event #$index (line, qty, acquired, term, proceeds, basis): expected ${reference.getOrNull(index)}, app ${app.getOrNull(index)}"
    }

    private fun moneyDifference(label: String, expected: String?, actual: BigDecimal): String? {
        val want = BigDecimal(expected ?: "0")
        val got = actual.setScale(2, RoundingMode.HALF_UP)
        return if (want.compareTo(got) == 0) null else "$label: expected $want, app ${got.toPlainString()}"
    }

    private fun countDifference(label: String, expected: Int?, actual: Int): String? =
        if ((expected ?: 0) == actual) null else "$label: expected ${expected ?: 0}, app $actual"

    private fun cents(value: BigDecimal) = value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun quantity(value: BigDecimal) = if (value.signum() == 0) "0" else value.stripTrailingZeros().toPlainString()

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
    private fun JsonObject.text(key: String): String = checkNotNull(textOrNull(key)) { "Missing $key" }
    private fun JsonObject.textOrNull(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    private companion object {
        const val MIN_FIXTURES = 34
    }
}
