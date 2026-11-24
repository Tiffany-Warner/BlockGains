package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.LotMatch
import com.tiffles.blockgains.domain.model.MatchingScope
import com.tiffles.blockgains.domain.model.TaxableEvent
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Test

/**
 * Calculator invariants over randomly generated transaction sets (coding-standards → Testing).
 * Fixed seeds, so any failure reproduces: the message names the seed.
 */
class TaxCalculatorInvariantsTest {

    private val calculator = TaxCalculator(FifoStrategy)
    private val settings = CalcSettings(defaultWallet = WALLETS.first())

    @Test
    fun randomSets_conserveUnitsAndBasis_andNeverThrow() {
        val results = (1..SEEDS).map { seed ->
            val txs = RandomTransactions(Random(seed)).generate(count = 120, from = START, to = END)
            calculator.calculate(txs, settings).also { assertInvariants("seed $seed", txs, it) }
        }

        // Guard against vacuous passes: the generator must exercise every path.
        val events = results.flatMap { it.taxableEvents }
        val issues = results.flatMap { it.issues }
        assertThat(events.count { it.scope == MatchingScope.UNIVERSAL }).isGreaterThan(1000)
        assertThat(events.count { it.scope == MatchingScope.PER_WALLET }).isGreaterThan(1000)
        assertThat(events.count { it.lots.size > 1 }).isGreaterThan(100)
        assertThat(events.count { it.type == TransactionType.SWAP }).isGreaterThan(100)
        assertThat(results.sumOf { it.adjustments.size }).isGreaterThan(100)
        assertThat(issues.count { it is CalcIssue.InsufficientLots }).isGreaterThan(100)
        assertThat(issues.count { it is CalcIssue.InvalidRow }).isGreaterThan(100)
    }

    @Test
    fun randomSets_withExactOpeningRecords_matchOnlyWithinWallets() {
        for (seed in 1..SEEDS) {
            val random = Random(seed)
            val generator = RandomTransactions(random)
            val before = generator.generate(count = 60, from = START, to = LAST_UNIVERSAL_DAY)
            val snapshot = calculator.calculate(before, settings).universalLotsAtTransition
            val openings = generator.openingsFor(snapshot)
            val after = generator.generate(count = 80, from = TaxRules.TRANSITION_DATE, to = END)
            val txs = before + openings + after

            val result = calculator.calculate(txs, settings)

            assertWithMessage("seed $seed").that(result.issues.filterIsInstance<CalcIssue.TransitionMismatch>()).isEmpty()
            assertInvariants("seed $seed", txs, result)
        }
    }

    private fun assertInvariants(label: String, txs: List<Transaction>, result: CalcResult) {
        val valid = txs.filter { TransactionValidator.validate(it) == null }
        // OPENING records replace an equal universal remainder, so count pre-2025 history instead.
        val history = valid.filter { it.type != TransactionType.OPENING }
        for (asset in history.flatMap { listOfNotNull(it.asset, it.toAsset) }.distinct()) {
            val acquired = history.sumOf { acquiredUnits(it, asset) }
            // Unmatched units (Open questions, B) never came from a lot, so they aren't counted.
            val disposed = result.taxableEvents.filter { it.asset == asset }.sumOf { e -> e.lots.filterNot { it.isUnmatched(e) }.sumOf { it.quantity } }
            val adjusted = result.adjustments.filter { it.asset == asset }.sumOf { it.quantity }
            val remaining = result.remainingLots.filter { it.asset == asset }.sumOf { it.quantity }
            assertWithMessage("$label units $asset").that(disposed + adjusted + remaining).isEquivalentAccordingToCompareTo(acquired)

            val basisIn = history.sumOf { acquiredBasis(it, asset) }
            val basisUsed = result.taxableEvents.filter { it.asset == asset }.sumOf { it.basis }
            val basisDropped = result.adjustments.filter { it.asset == asset }.sumOf { it.basisRemoved }
            val basisLeft = result.remainingLots.filter { it.asset == asset }.sumOf { it.basis }
            assertWithMessage("$label basis $asset").that(basisUsed + basisDropped + basisLeft).isEquivalentAccordingToCompareTo(basisIn)
        }
        // A transfer's own shortfall creates no event; its network-fee shortfall does (R13).
        val shortBy = result.issues.filterIsInstance<CalcIssue.InsufficientLots>()
            .filter { it.type != TransactionType.TRANSFER || it.networkFee }
            .associate { it.transactionId to it.shortBy }
        for (event in result.taxableEvents) {
            val unmatched = event.lots.filter { it.isUnmatched(event) }
            assertWithMessage("$label unmatched units = shortfall").that(unmatched.sumOf { it.quantity })
                .isEquivalentAccordingToCompareTo(shortBy[event.transactionId] ?: BigDecimal.ZERO)
            for (lot in unmatched) {
                assertWithMessage("$label unmatched basis").that(lot.basis.signum()).isEqualTo(0)
                assertWithMessage("$label unmatched term").that(lot.term).isEqualTo(HoldingTerm.SHORT)
                assertWithMessage("$label unmatched marker").that(lot.basisUnknown).isTrue()
                assertWithMessage("$label unmatched date").that(lot.acquiredDate).isNull()
            }
            for (lot in event.lots - unmatched.toSet()) {
                assertWithMessage("$label matched date").that(lot.acquiredDate).isNotNull()
                assertWithMessage("$label lot after disposal").that(lot.acquiredDate!!.isAfter(event.date)).isFalse()
                if (event.scope == MatchingScope.PER_WALLET) {
                    assertWithMessage("$label cross-wallet match").that(lot.wallet).isEqualTo(event.wallet)
                }
            }
            val expectedScope = if (event.date.isBefore(TaxRules.TRANSITION_DATE)) MatchingScope.UNIVERSAL else MatchingScope.PER_WALLET
            assertThat(event.scope).isEqualTo(expectedScope)
        }
    }

    private fun LotMatch.isUnmatched(event: TaxableEvent) = sourceTransactionId == event.transactionId

    private fun acquiredUnits(tx: Transaction, asset: String): BigDecimal = when {
        tx.asset == asset && (tx.type == TransactionType.BUY || tx.type == TransactionType.INCOME) -> tx.quantity
        tx.type == TransactionType.SWAP && tx.toAsset == asset -> tx.toQuantity ?: BigDecimal.ZERO
        else -> BigDecimal.ZERO
    }

    private fun acquiredBasis(tx: Transaction, asset: String): BigDecimal = when {
        tx.asset == asset && tx.type == TransactionType.BUY -> (tx.usdValue ?: BigDecimal.ZERO) + tx.fee
        tx.asset == asset && tx.type == TransactionType.INCOME -> tx.usdValue ?: BigDecimal.ZERO
        tx.type == TransactionType.SWAP && tx.toAsset == asset -> tx.totalUsd ?: BigDecimal.ZERO
        else -> BigDecimal.ZERO
    }

    private companion object {
        const val SEEDS = 200
        val START: LocalDate = LocalDate.of(2022, 1, 1)
        val LAST_UNIVERSAL_DAY: LocalDate = LocalDate.of(2024, 12, 31)
        val END: LocalDate = LocalDate.of(2026, 6, 30)
        val WALLETS = listOf("FAKE-WALLET-1", "FAKE-WALLET-2", "FAKE-EXCHANGE-1")
    }
}

/** Generates made-up, mostly-valid transaction sets, including some bad rows and impossible sales. */
private class RandomTransactions(private val random: Random) {
    private var nextId = 1L
    private val assets = listOf("FAKEA", "FAKEB", "FAKEC")
    private val wallets = listOf("FAKE-WALLET-1", "FAKE-WALLET-2", "FAKE-EXCHANGE-1")

    fun generate(count: Int, from: LocalDate, to: LocalDate): List<Transaction> =
        List(count) { next(randomDate(from, to)) }

    /** Splits every snapshot lot across 1–2 random wallets, conserving quantity and basis exactly. */
    fun openingsFor(snapshot: List<com.tiffles.blockgains.domain.model.Lot>): List<Transaction> = snapshot.flatMap { lot ->
        val split = random.nextBoolean() && lot.quantity > BigDecimal("0.000000002")
        val firstQty = if (split) lot.quantity.divide(BigDecimal(2), 9, java.math.RoundingMode.DOWN) else lot.quantity
        val firstBasis = if (split) lot.basis.multiply(firstQty).divide(lot.quantity, TaxRules.MATH) else lot.basis
        val parts = listOf(firstQty to firstBasis) +
            if (split) listOf((lot.quantity - firstQty) to (lot.basis - firstBasis)) else emptyList()
        parts.map { (qty, basis) ->
            base(TransactionType.OPENING, TaxRules.TRANSITION_DATE, lot.asset, wallets.random(random), qty)
                .copy(totalUsd = basis, acquiredDate = lot.acquiredDate, basisUnknown = lot.basisUnknown)
        }
    }

    private fun next(date: LocalDate): Transaction {
        val asset = assets.random(random)
        val wallet = wallets.random(random)
        val qty = quantity()
        return when (random.nextInt(100)) {
            in 0..34 -> base(TransactionType.BUY, date, asset, wallet, qty)
                .copy(priceUsd = money(), feeUsd = maybeFee(), basisUnknown = random.nextInt(10) == 0)
            in 35..59 -> base(TransactionType.SELL, date, asset, wallet, qty).copy(priceUsd = money(), feeUsd = maybeFee())
            in 60..69 -> base(TransactionType.INCOME, date, asset, wallet, qty).copy(totalUsd = money())
            in 70..79 -> withMaybeNetworkFee(base(TransactionType.TRANSFER, date, asset, wallet, qty).copy(toWallet = (wallets - wallet).random(random)))
            in 80..89 -> base(TransactionType.SWAP, date, asset, wallet, qty).copy(
                toAsset = (assets - asset).random(random), toQuantity = quantity(), totalUsd = money(), feeUsd = maybeFee(),
            )
            in 90..94 -> base(TransactionType.ADJUST, date, asset, wallet, qty.negate())
            else -> base(TransactionType.SELL, date, asset, wallet, qty.negate()) // invalid row
        }
    }

    /** R13: about half of transfers pay a fee in coins, a third of those in another asset. */
    private fun withMaybeNetworkFee(tx: Transaction): Transaction = if (random.nextBoolean()) {
        tx
    } else {
        tx.copy(
            feeQuantity = BigDecimal.valueOf(random.nextLong(1, 50_000_000L), 9),
            feeUsd = money(),
            feeAsset = if (random.nextInt(3) == 0) (assets - tx.asset).random(random) else null,
        )
    }

    private fun base(type: TransactionType, date: LocalDate, asset: String, wallet: String, qty: BigDecimal) =
        Transaction(id = nextId++, type = type, date = date, asset = asset, wallet = wallet, quantity = qty)

    private fun quantity(): BigDecimal = BigDecimal.valueOf(random.nextLong(1, 5_000_000_000L), 9)
    private fun money(): BigDecimal = BigDecimal.valueOf(random.nextLong(0, 10_000_000L), 2)
    private fun maybeFee(): BigDecimal? = if (random.nextBoolean()) BigDecimal.valueOf(random.nextLong(0, 2_000L), 2) else null

    private fun randomDate(from: LocalDate, to: LocalDate): LocalDate =
        from.plusDays(random.nextLong(0, to.toEpochDay() - from.toEpochDay() + 1))
}
