package com.tiffles.blockgains.domain.calc

import com.tiffles.blockgains.domain.model.Adjustment
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.IncomeEvent
import com.tiffles.blockgains.domain.model.Lot
import com.tiffles.blockgains.domain.model.LotMatch
import com.tiffles.blockgains.domain.model.MatchingScope
import com.tiffles.blockgains.domain.model.TaxableEvent
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Pure, deterministic tax engine: `calculate(transactions, settings): CalcResult`.
 * Implements `tax-rules.md` R1–R11. Data problems become [CalcIssue]s, never exceptions (R10).
 */
class TaxCalculator(private val strategy: LotMatchingStrategy) {

    @Inject constructor() : this(FifoStrategy)

    fun calculate(transactions: List<Transaction>, settings: CalcSettings): CalcResult =
        CalculationRun(strategy, settings).execute(transactions)
}

/** State for one calculation. Not thread-safe; create one per call. */
private class CalculationRun(strategy: LotMatchingStrategy, private val settings: CalcSettings) {

    private val pools = LotPools(strategy)
    private val events = mutableListOf<TaxableEvent>()
    private val income = mutableListOf<IncomeEvent>()
    private val adjustments = mutableListOf<Adjustment>()
    private val issues = mutableListOf<CalcIssue>()
    private var universalAtTransition: List<Lot> = emptyList()
    private var perWallet = false

    fun execute(transactions: List<Transaction>): CalcResult {
        val valid = transactions.filter { tx ->
            val reason = TransactionValidator.validate(tx) ?: return@filter true
            issues += CalcIssue.InvalidRow(reason, transactionId = tx.id, date = tx.date, asset = tx.asset)
            false
        }
        val (openings, records) = valid.partition { it.type == TransactionType.OPENING }
        val assetsWithHistory = TaxRules.assetsWithHistory(records)

        for (tx in processingOrder(records)) {
            if (!perWallet && !tx.date.isBefore(TaxRules.TRANSITION_DATE)) applyTransition(openings, assetsWithHistory)
            process(tx)
        }
        if (!perWallet) applyTransition(openings, assetsWithHistory)

        return CalcResult(
            taxableEvents = events.toList(),
            incomeEvents = income.toList(),
            adjustments = adjustments.toList(),
            remainingLots = pools.allLots(),
            universalLotsAtTransition = universalAtTransition,
            issues = issues.toList(),
        )
    }

    /**
     * Chronological. Same date (R3): acquisitions first, then moves (TRANSFER/SWAP, in
     * input order so a same-day chain keeps the file's order), then disposals.
     */
    private fun processingOrder(records: List<Transaction>): List<Transaction> =
        records.withIndex()
            .sortedWith(compareBy({ it.value.date }, { samedayPhase(it.value.type) }, { it.index }))
            .map { it.value }

    private fun samedayPhase(type: TransactionType): Int = when (type) {
        TransactionType.OPENING, TransactionType.BUY, TransactionType.INCOME -> 0
        TransactionType.TRANSFER, TransactionType.SWAP -> 1
        TransactionType.ADJUST, TransactionType.SELL -> 2
    }

    private fun process(tx: Transaction) {
        when (tx.type) {
            TransactionType.BUY -> buy(tx)
            TransactionType.INCOME -> income(tx)
            TransactionType.SELL -> dispose(tx, amount = value(tx), receivedAsset = null)
            TransactionType.SWAP -> swap(tx)
            TransactionType.TRANSFER -> transfer(tx)
            TransactionType.ADJUST -> adjust(tx)
            TransactionType.OPENING -> Unit // Applied at the transition (R4).
        }
    }

    // R7: buy fee adds to basis. R9: a $0 buy not marked unknown-basis is flagged.
    private fun buy(tx: Transaction) {
        val cost = value(tx)
        if (cost.signum() == 0 && !tx.basisUnknown) {
            issues += CalcIssue.ZeroPriceBuy(tx.id, tx.date, tx.asset, tx.wallet, tx.quantity)
        }
        addLot(tx, asset = tx.asset, quantity = tx.quantity, basis = cost + tx.fee)
    }

    // R8: income at FMV, which also becomes the new lot's basis.
    private fun income(tx: Transaction) {
        val fmv = value(tx)
        income += IncomeEvent(tx.id, tx.date, tx.asset, tx.assetClass, tx.wallet, tx.quantity, fmv, tx.note)
        addLot(tx, asset = tx.asset, quantity = tx.quantity, basis = fmv)
    }

    // R6: dispose of A for V (less fee), then acquire B with basis V on the same date and wallet.
    private fun swap(tx: Transaction) {
        val fmv = tx.totalUsd ?: BigDecimal.ZERO
        val received = checkNotNull(tx.toAsset) { "validated" }
        dispose(tx, amount = fmv, receivedAsset = received)
        addLot(tx, asset = received, quantity = checkNotNull(tx.toQuantity) { "validated" }, basis = fmv)
    }

    // R5: moves lots FIFO with original dates and basis. Pre-2025 transfers don't affect matching (R4).
    // R13: a network fee paid in coins is disposed of first (oldest lots pay it), before 2025 too.
    private fun transfer(tx: Transaction) {
        disposeNetworkFee(tx)
        if (!perWallet) return
        val destination = checkNotNull(tx.toWallet) { "validated" }
        val withdrawal = withdraw(tx, tx.quantity)
        withdrawal.taken.forEach { pools.add(PoolKey(tx.asset, destination), it.copy(wallet = destination)) }
    }

    /**
     * R13: the fee units are a disposal from the source pool: amount realized = `fee_usd`, no
     * transaction cost (own-wallet transfer fees aren't one), and nothing is added to the moved
     * units' basis. A fee shortfall gets the same $0-basis slice as a SELL (R3).
     */
    private fun disposeNetworkFee(tx: Transaction) {
        val asset = tx.networkFeeAsset ?: return
        val quantity = checkNotNull(tx.feeQuantity) { "validated" }
        dispose(tx, amount = tx.fee, receivedAsset = null, asset = asset, quantity = quantity, fee = BigDecimal.ZERO, networkFee = true)
    }

    // R9: negative ADJUST removes units FIFO with no gain or loss; their basis is dropped.
    private fun adjust(tx: Transaction) {
        val withdrawal = withdraw(tx, tx.quantity.abs())
        if (withdrawal.taken.isEmpty()) return
        adjustments += Adjustment(
            transactionId = tx.id,
            date = tx.date,
            asset = tx.asset,
            wallet = tx.wallet,
            quantity = withdrawal.quantity,
            basisRemoved = withdrawal.taken.sumOf { it.basis },
        )
    }

    /**
     * R1/R3/R7: match lots for a disposal and split the amount realized (gross [amount] less fee)
     * pro rata by quantity. R3/R10: if the pool is short, the shortfall is reported and the
     * unmatched units become one more slice with $0 basis, short-term (tax-rules → Open questions, B).
     * [asset], [quantity] and [fee] differ from the row's own only for a transfer's network fee (R13).
     */
    private fun dispose(
        tx: Transaction,
        amount: BigDecimal,
        receivedAsset: String?,
        asset: String = tx.asset,
        quantity: BigDecimal = tx.quantity,
        fee: BigDecimal = tx.fee,
        networkFee: Boolean = false,
    ) {
        val withdrawal = withdraw(tx, quantity, asset, networkFee)
        val unmatched = if (withdrawal.isShort) unmatchedSlice(tx, asset, withdrawal.shortBy) else null
        val lots = allocate(withdrawal.taken + listOfNotNull(unmatched), amount - fee, fee, tx.date, unmatched)
        events += TaxableEvent(
            transactionId = tx.id,
            type = tx.type,
            date = tx.date,
            asset = asset,
            assetClass = tx.assetClass,
            wallet = tx.wallet,
            scope = if (perWallet) MatchingScope.PER_WALLET else MatchingScope.UNIVERSAL,
            lots = lots,
            receivedAsset = receivedAsset,
        )
    }

    /**
     * Units a disposal used without a matching lot: $0 basis, marked unknown basis, dated the
     * disposal date so R2 makes them short-term (the match reports the date as unknown). Its source
     * is the disposal itself.
     */
    private fun unmatchedSlice(tx: Transaction, asset: String, quantity: BigDecimal) = Lot(
        sourceTransactionId = tx.id,
        asset = asset,
        assetClass = tx.assetClass,
        wallet = tx.wallet,
        acquiredDate = tx.date,
        quantity = quantity,
        basis = BigDecimal.ZERO,
        basisUnknown = true,
    )

    /** Splits [proceeds] and [fee] across lot slices by quantity; the last slice takes the remainder so sums are exact. */
    private fun allocate(
        taken: List<Lot>,
        proceeds: BigDecimal,
        fee: BigDecimal,
        disposedOn: LocalDate,
        unmatched: Lot? = null,
    ): List<LotMatch> {
        val total = taken.sumOf { it.quantity }
        var proceedsLeft = proceeds
        var feeLeft = fee
        return taken.mapIndexed { index, lot ->
            val last = index == taken.lastIndex
            val lotProceeds = if (last) proceedsLeft else prorate(proceeds, lot.quantity, total)
            val lotFee = if (last) feeLeft else prorate(fee, lot.quantity, total)
            proceedsLeft -= lotProceeds
            feeLeft -= lotFee
            LotMatch(
                sourceTransactionId = lot.sourceTransactionId,
                wallet = lot.wallet,
                acquiredDate = lot.acquiredDate.takeUnless { lot === unmatched },
                quantity = lot.quantity,
                basis = lot.basis,
                proceeds = lotProceeds,
                fee = lotFee,
                term = TaxRules.holdingTerm(lot.acquiredDate, disposedOn), // R2
                basisUnknown = lot.basisUnknown,
            )
        }
    }

    /** Takes units from the pool R3 selects for [tx]; reports any shortfall. */
    private fun withdraw(tx: Transaction, quantity: BigDecimal, asset: String = tx.asset, networkFee: Boolean = false): Withdrawal {
        val withdrawal = pools.withdraw(poolKey(asset, tx.wallet), quantity, tx.date)
        if (withdrawal.isShort) {
            issues += CalcIssue.InsufficientLots(
                transactionId = tx.id,
                type = tx.type,
                date = tx.date,
                asset = asset,
                wallet = tx.wallet,
                pooled = !perWallet,
                requested = quantity,
                available = withdrawal.quantity,
                networkFee = networkFee,
            )
        }
        return withdrawal
    }

    /**
     * R4: on 2025-01-01 each asset's universal remainder is replaced by its OPENING records,
     * or, with none, moved unchanged to the default wallet. Totals that differ are reported for
     * assets with records before 2025-01-01; openings with no earlier history don't warn.
     */
    private fun applyTransition(openings: List<Transaction>, assetsWithHistory: Set<String>) {
        perWallet = true
        val universal = pools.universalKeys().associate { it.asset to pools.remove(it) }
        universalAtTransition = universal.values.flatten()
        val openingsByAsset = openings.groupBy { it.asset }

        for (asset in (universal.keys + openingsByAsset.keys)) {
            val remainder = universal[asset].orEmpty()
            val records = openingsByAsset[asset]
            if (records.isNullOrEmpty()) {
                remainder.forEach { pools.add(PoolKey(asset, settings.defaultWallet), it.copy(wallet = settings.defaultWallet)) }
                continue
            }
            records.forEach { addOpeningLot(it) }
            val expected = remainder.sumOf { it.quantity }
            val opening = records.sumOf { it.quantity }
            // R4: only assets with records before 2025-01-01 can disagree with their history.
            if (asset in assetsWithHistory && expected.compareTo(opening) != 0) {
                issues += CalcIssue.TransitionMismatch(asset, TaxRules.TRANSITION_DATE, expected, opening)
            }
        }
    }

    /** OPENING keeps the original acquired date (R2) and original cost basis. */
    private fun addOpeningLot(tx: Transaction) {
        val lot = Lot(
            sourceTransactionId = tx.id,
            asset = tx.asset,
            assetClass = tx.assetClass,
            wallet = tx.wallet,
            acquiredDate = checkNotNull(tx.acquiredDate) { "validated" },
            quantity = tx.quantity,
            basis = value(tx),
            basisUnknown = tx.basisUnknown,
        )
        pools.add(PoolKey(tx.asset, tx.wallet), lot)
    }

    private fun addLot(tx: Transaction, asset: String, quantity: BigDecimal, basis: BigDecimal) {
        val lot = Lot(
            sourceTransactionId = tx.id,
            asset = asset,
            assetClass = tx.assetClass,
            wallet = tx.wallet,
            acquiredDate = tx.date,
            quantity = quantity,
            basis = basis,
            basisUnknown = tx.basisUnknown,
        )
        pools.add(poolKey(asset, tx.wallet), lot)
    }

    private fun poolKey(asset: String, wallet: String) = PoolKey(asset, if (perWallet) wallet else null)

    private fun value(tx: Transaction): BigDecimal = tx.usdValue ?: BigDecimal.ZERO

    private fun prorate(total: BigDecimal, part: BigDecimal, whole: BigDecimal): BigDecimal =
        total.multiply(part).divide(whole, TaxRules.MATH)
}
