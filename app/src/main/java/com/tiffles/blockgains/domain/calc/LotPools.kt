package com.tiffles.blockgains.domain.calc

import com.tiffles.blockgains.domain.model.Lot
import java.math.BigDecimal
import java.time.LocalDate

/** A pool is per asset (universal, [wallet] = null) or per (asset, wallet). R3. */
internal data class PoolKey(val asset: String, val wallet: String?)

/** Result of taking units out of a pool. [taken] are lot slices with their share of basis. */
internal data class Withdrawal(val taken: List<Lot>, val requested: BigDecimal) {
    val quantity: BigDecimal get() = taken.sumOf { it.quantity }
    val shortBy: BigDecimal get() = requested - quantity
    val isShort: Boolean get() = shortBy.signum() > 0
}

/**
 * Mutable lot pools for one calculation run. Each pool is kept in acquisition order
 * (acquired date, then insertion order), which is what FIFO and transfers need.
 */
internal class LotPools(private val strategy: LotMatchingStrategy) {

    private val pools = linkedMapOf<PoolKey, MutableList<Lot>>()

    fun add(key: PoolKey, lot: Lot) {
        val pool = pools.getOrPut(key) { mutableListOf() }
        // Insert after every lot acquired on or before this one (stable for equal dates).
        val index = pool.indexOfFirst { it.acquiredDate.isAfter(lot.acquiredDate) }
        if (index < 0) pool += lot else pool.add(index, lot)
    }

    /** Takes [quantity] units via the strategy, splitting the last lot if needed. Basis moves pro rata. */
    fun withdraw(key: PoolKey, quantity: BigDecimal, date: LocalDate): Withdrawal {
        val pool = pools[key] ?: return Withdrawal(emptyList(), quantity)
        val takes = strategy.select(pool, quantity, date)
        val taken = takes.map { take -> slice(pool[take.index], take.quantity) }
        // Apply in descending index order so earlier indices stay valid.
        takes.sortedByDescending { it.index }.forEach { take ->
            val lot = pool[take.index]
            val remainder = lot.quantity - take.quantity
            if (remainder.signum() == 0) {
                pool.removeAt(take.index)
            } else {
                val usedBasis = basisShare(lot, take.quantity)
                pool[take.index] = lot.copy(quantity = remainder, basis = lot.basis - usedBasis)
            }
        }
        return Withdrawal(taken, quantity)
    }

    fun universalKeys(): List<PoolKey> = pools.keys.filter { it.wallet == null }

    fun lots(key: PoolKey): List<Lot> = pools[key].orEmpty().toList()

    fun remove(key: PoolKey): List<Lot> = pools.remove(key).orEmpty()

    fun allLots(): List<Lot> = pools.values.flatten()

    private fun slice(lot: Lot, quantity: BigDecimal): Lot =
        lot.copy(quantity = quantity, basis = basisShare(lot, quantity))

    /** Basis of [quantity] units of [lot]: whole basis when taking everything, else pro rata. */
    private fun basisShare(lot: Lot, quantity: BigDecimal): BigDecimal =
        if (quantity.compareTo(lot.quantity) == 0) {
            lot.basis
        } else {
            lot.basis.multiply(quantity).divide(lot.quantity, TaxRules.MATH)
        }
}
