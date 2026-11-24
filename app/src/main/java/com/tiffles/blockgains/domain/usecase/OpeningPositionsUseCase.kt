package com.tiffles.blockgains.domain.usecase

import com.tiffles.blockgains.domain.calc.TaxRules
import com.tiffles.blockgains.domain.model.Lot
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import javax.inject.Inject

/** Part of a universal lot assigned to one wallet. */
data class LotAllocation(val wallet: String, val quantity: BigDecimal)

/** One universal lot (as of 2025-01-01) and how it's split across wallets. */
data class OpeningLotDraft(val lot: Lot, val allocations: List<LotAllocation>) {
    val allocated: BigDecimal get() = allocations.sumOf { it.quantity }
    val unassigned: BigDecimal get() = lot.quantity - allocated
    val problem: DraftProblem? get() = when {
        allocations.any { it.wallet.isBlank() } -> DraftProblem.BLANK_WALLET
        allocations.any { it.quantity.signum() <= 0 } -> DraftProblem.NON_POSITIVE
        unassigned.signum() > 0 -> DraftProblem.UNASSIGNED
        unassigned.signum() < 0 -> DraftProblem.OVER_ALLOCATED
        else -> null
    }
}

enum class DraftProblem { BLANK_WALLET, NON_POSITIVE, UNASSIGNED, OVER_ALLOCATED }

/**
 * Opening positions helper (R4, ui-guidelines → Import): turns what the universal pool held
 * on 2025-01-01 into per-wallet OPENING records, keeping each lot's date and basis.
 */
class OpeningPositionsUseCase @Inject constructor() {

    /**
     * Starting drafts for one asset. With no saved OPENING records everything goes to the default
     * wallet. Otherwise saved records are matched back to lots by acquired date, FIFO; whatever
     * doesn't match is left unassigned so the user sees it.
     */
    fun drafts(lots: List<Lot>, saved: List<Transaction>, defaultWallet: String): List<OpeningLotDraft> {
        if (saved.isEmpty()) return lots.map { OpeningLotDraft(it, listOf(LotAllocation(defaultWallet, it.quantity))) }
        val queues = saved.groupBy { it.acquiredDate }
            .mapValues { (_, records) -> ArrayDeque(records.map { LotAllocation(it.wallet, it.quantity) }) }
        return lots.map { lot -> OpeningLotDraft(lot, takeFrom(queues[lot.acquiredDate], lot.quantity)) }
    }

    /** OPENING records for valid drafts. Basis splits pro rata; the last allocation takes the remainder. */
    fun toOpeningRecords(drafts: List<OpeningLotDraft>): List<Transaction> = drafts.flatMap { draft ->
        require(draft.problem == null) { "Draft has a problem: ${draft.problem}" }
        var basisLeft = draft.lot.basis
        draft.allocations.mapIndexed { index, allocation ->
            val basis = if (index == draft.allocations.lastIndex) {
                basisLeft
            } else {
                draft.lot.basis.multiply(allocation.quantity).divide(draft.lot.quantity, TaxRules.MATH)
            }
            basisLeft -= basis
            Transaction(
                type = TransactionType.OPENING,
                date = TaxRules.TRANSITION_DATE,
                asset = draft.lot.asset,
                assetClass = draft.lot.assetClass,
                wallet = allocation.wallet.trim(),
                quantity = allocation.quantity,
                totalUsd = basis,
                acquiredDate = draft.lot.acquiredDate,
                basisUnknown = draft.lot.basisUnknown,
            )
        }
    }

    private fun takeFrom(queue: ArrayDeque<LotAllocation>?, quantity: BigDecimal): List<LotAllocation> {
        val taken = linkedMapOf<String, BigDecimal>()
        var needed = quantity
        while (needed.signum() > 0 && !queue.isNullOrEmpty()) {
            val next = queue.removeFirst()
            val take = next.quantity.min(needed)
            taken.merge(next.wallet, take, BigDecimal::add)
            needed -= take
            val left = next.quantity - take
            if (left.signum() > 0) queue.addFirst(next.copy(quantity = left))
        }
        return taken.map { (wallet, qty) -> LotAllocation(wallet, qty) }
    }
}
