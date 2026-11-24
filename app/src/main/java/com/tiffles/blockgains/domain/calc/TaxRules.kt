package com.tiffles.blockgains.domain.calc

import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Constants and small rules from `tax-rules.md`. */
object TaxRules {
    /** R3/R4: per-wallet matching starts here; OPENING records are dated this day. */
    val TRANSITION_DATE: LocalDate = LocalDate.of(2025, 1, 1)

    /** R11: annual capital-loss offset against other income (display only). */
    val LOSS_LIMIT: BigDecimal = BigDecimal("3000")
    val LOSS_LIMIT_MARRIED_SEPARATE: BigDecimal = BigDecimal("1500")

    /** Price/total disagreement tolerated on import (import-format-spec → Unified format). */
    val PRICE_TOTAL_TOLERANCE: BigDecimal = BigDecimal("0.01")

    /** Precision for pro-rata division; results are only rounded for display (Rounding). */
    val MATH: MathContext = MathContext.DECIMAL128

    /**
     * R2: long-term means held MORE than one year, counting from the day after acquisition.
     * `plusYears(1)` maps Feb 29 to Feb 28, so a leap-day lot turns long-term on Mar 1.
     */
    fun holdingTerm(acquired: LocalDate, disposed: LocalDate): HoldingTerm =
        if (disposed.isAfter(acquired.plusYears(1))) HoldingTerm.LONG else HoldingTerm.SHORT

    /**
     * R4: assets with records dated before [TRANSITION_DATE], as the main asset or as a swap's
     * received asset. Only these can get a `TransitionMismatch`. A `to_asset` on any other type is
     * ignored, like every type-specific column the type doesn't use.
     */
    fun assetsWithHistory(records: List<Transaction>): Set<String> =
        records.filter { it.date.isBefore(TRANSITION_DATE) }
            .flatMap { tx -> listOfNotNull(tx.asset, tx.toAsset.takeIf { tx.type == TransactionType.SWAP }) }
            .toSet()
}
