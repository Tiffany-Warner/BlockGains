package com.tiffles.blockgains.domain.calc

import com.tiffles.blockgains.domain.model.Lot
import java.math.BigDecimal
import java.time.LocalDate

/** Take [quantity] units from the lot at [index] of the pool. */
data class LotTake(val index: Int, val quantity: BigDecimal)

/**
 * Chooses which lots a disposal consumes (R3). FIFO now; HIFO/specific ID later
 * plug in here without touching the calculator.
 */
fun interface LotMatchingStrategy {
    /**
     * @param pool lots in acquisition order (earliest acquired first).
     * @return takes summing to at most [quantity], only from lots acquired on or before [date].
     */
    fun select(pool: List<Lot>, quantity: BigDecimal, date: LocalDate): List<LotTake>
}

/** R3: first in, first out by acquired date. Never matches a lot acquired after the disposal. */
object FifoStrategy : LotMatchingStrategy {
    override fun select(pool: List<Lot>, quantity: BigDecimal, date: LocalDate): List<LotTake> {
        val takes = mutableListOf<LotTake>()
        var needed = quantity
        for ((index, lot) in pool.withIndex()) {
            if (needed.signum() <= 0) break
            if (lot.acquiredDate.isAfter(date)) continue
            val take = lot.quantity.min(needed)
            takes += LotTake(index, take)
            needed -= take
        }
        return takes
    }
}
