package com.tiffles.blockgains.data.calculator

import com.tiffles.blockgains.data.model.BuyTransaction
import com.tiffles.blockgains.data.model.SellTransaction
import com.tiffles.blockgains.data.model.TaxableEvent
import com.tiffles.blockgains.data.model.YearSummary
import java.math.BigDecimal
import java.time.temporal.ChronoUnit

/**
 * Calculates capital gains using FIFO (First In, First Out) method
 */
object FifoCalculator {

    /**
     * Calculate taxable events by matching sells with buys using FIFO
     */
    fun calculateTaxableEvents(
        buys: List<BuyTransaction>,
        sells: List<SellTransaction>
    ): List<TaxableEvent> {
        val taxableEvents = mutableListOf<TaxableEvent>()

        // Create a mutable list to track remaining quantities from buys
        val remainingBuys = buys.map { buy ->
            RemainingBuy(
                transaction = buy,
                remainingQuantity = buy.quantity
            )
        }.toMutableList()

        // Process each sell transaction
        for (sell in sells) {
            var remainingToSell = sell.quantitySold

            // Match with buys using FIFO
            while (remainingToSell > BigDecimal.ZERO) {
                // Find the earliest buy with remaining quantity
                val buyIndex = remainingBuys.indexOfFirst {
                    it.remainingQuantity > BigDecimal.ZERO
                }

                if (buyIndex == -1) {
                    throw IllegalStateException(
                        "Insufficient buy transactions to cover sell on ${sell.dateSold}. " +
                                "Remaining quantity to match: $remainingToSell"
                    )
                }

                val remainingBuy = remainingBuys[buyIndex]
                val buy = remainingBuy.transaction

                // Calculate how much to match from this buy
                val quantityToMatch = minOf(remainingToSell, remainingBuy.remainingQuantity)

                // Calculate gain/loss
                val costBasis = buy.purchasePricePerUnit
                val salePrice = sell.salePricePerUnit
                val gain = (salePrice - costBasis) * quantityToMatch

                // Determine if long-term (held > 1 year)
                val daysHeld = ChronoUnit.DAYS.between(buy.dateBought, sell.dateSold)
                val isLongTerm = daysHeld > 365

                // Create taxable event
                val event = TaxableEvent(
                    sellDate = sell.dateSold,
                    buyDate = buy.dateBought,
                    quantity = quantityToMatch,
                    costBasis = costBasis,
                    salePrice = salePrice,
                    gain = gain,
                    isLongTerm = isLongTerm
                )
                taxableEvents.add(event)

                // Update remaining quantities
                remainingBuy.remainingQuantity -= quantityToMatch
                remainingToSell -= quantityToMatch
            }
        }

        return taxableEvents
    }

    /**
     * Group taxable events by year and calculate summary
     */
    fun calculateYearSummaries(events: List<TaxableEvent>): Map<Int, YearSummary> {
        val eventsByYear = events.groupBy { it.sellDate.year }

        return eventsByYear.mapValues { (year, yearEvents) ->
            val shortTermEvents = yearEvents.filter { !it.isLongTerm }
            val longTermEvents = yearEvents.filter { it.isLongTerm }

            val shortTermGain = shortTermEvents.sumOf { it.gain }
            val longTermGain = longTermEvents.sumOf { it.gain }

            YearSummary(
                year = year,
                shortTermGain = shortTermGain,
                longTermGain = longTermGain,
                taxableEvents = yearEvents.sortedByDescending { it.sellDate }
            )
        }
    }

    /**
     * Helper class to track remaining quantity from a buy transaction
     */
    private data class RemainingBuy(
        val transaction: BuyTransaction,
        var remainingQuantity: BigDecimal
    )
}