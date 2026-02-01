package com.tiffles.blockgains.data.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Represents a purchase transaction
 */
data class BuyTransaction(
    val dateBought: LocalDate,
    val quantity: BigDecimal,
    val purchasePricePerUnit: BigDecimal
) {
    val totalCost: BigDecimal
        get() = quantity * purchasePricePerUnit
}

/**
 * Represents a sale transaction
 */
data class SellTransaction(
    val dateSold: LocalDate,
    val quantitySold: BigDecimal,
    val salePricePerUnit: BigDecimal
) {
    val totalProceeds: BigDecimal
        get() = quantitySold * salePricePerUnit
}

/**
 * Represents a matched transaction for tax reporting
 * This shows which portion of a sell was matched with which buy using FIFO
 */
data class TaxableEvent(
    val sellDate: LocalDate,
    val buyDate: LocalDate,
    val quantity: BigDecimal,
    val costBasis: BigDecimal, // Purchase price per unit
    val salePrice: BigDecimal, // Sale price per unit
    val gain: BigDecimal, // Total gain/loss for this event
    val isLongTerm: Boolean // Held for more than 1 year
) {
    val totalCost: BigDecimal
        get() = quantity * costBasis

    val totalProceeds: BigDecimal
        get() = quantity * salePrice
}

/**
 * Summary of capital gains/losses for a specific year
 */
data class YearSummary(
    val year: Int,
    val shortTermGain: BigDecimal,
    val longTermGain: BigDecimal,
    val taxableEvents: List<TaxableEvent>
) {
    val totalGain: BigDecimal
        get() = shortTermGain + longTermGain
}