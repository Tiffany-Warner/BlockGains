package com.tiffles.blockgains.domain.usecase

import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.Transaction
import java.math.BigDecimal
import javax.inject.Inject

/** One tax year's totals, unrounded (Rounding: round only for display/export). */
data class YearSummary(
    val year: Int,
    val shortTerm: BigDecimal,
    val longTerm: BigDecimal,
    val ordinaryIncome: BigDecimal,
    val eventCount: Int,
    val issues: List<CalcIssue>,
) {
    /** R11: short- and long-term net together. */
    val net: BigDecimal get() = shortTerm + longTerm

    /** R11: a net loss only offsets up to $3,000 of other income per year; show the note. */
    val showsLossLimitNote: Boolean get() = net.signum() < 0
}

/** Netting for a year and asset-class filter (R11). */
class SummarizeYearUseCase @Inject constructor() {

    operator fun invoke(result: CalcResult, year: Int, filter: AssetFilter): YearSummary {
        val events = result.taxableEvents.filter { it.date.year == year && filter.matches(it.assetClass) }
        val income = result.incomeEvents.filter { it.date.year == year && filter.matches(it.assetClass) }
        return YearSummary(
            year = year,
            shortTerm = events.sumOf { it.shortTermGain },
            longTerm = events.sumOf { it.longTermGain },
            ordinaryIncome = income.sumOf { it.valueUsd },
            eventCount = events.size,
            issues = issuesForYear(result.issues, year),
        )
    }

    /** Issues dated in [year], plus undated ones (they could affect any year). */
    fun issuesForYear(issues: List<CalcIssue>, year: Int): List<CalcIssue> =
        issues.filter { it.date == null || it.date?.year == year }
}

/** Years with any recorded activity, newest first. */
fun activityYears(transactions: List<Transaction>): List<Int> =
    transactions.map { it.date.year }.distinct().sortedDescending()

/** ui-guidelines → Summary: current year if it has activity, else the latest year with data. */
fun defaultYear(years: List<Int>, currentYear: Int): Int =
    if (years.isEmpty() || currentYear in years) currentYear else years.max()

/** The user's choice if it still has activity, else [defaultYear]. */
fun selectedYear(choice: Int?, years: List<Int>, currentYear: Int): Int =
    choice?.takeIf { it in years } ?: defaultYear(years, currentYear)
