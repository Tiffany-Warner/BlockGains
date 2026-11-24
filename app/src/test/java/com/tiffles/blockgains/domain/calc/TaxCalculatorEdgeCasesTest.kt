package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.Lot
import com.tiffles.blockgains.domain.model.MatchingScope
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import org.junit.Test

class TaxCalculatorEdgeCasesTest {

    @Test
    fun emptyInput_givesEmptyResult() {
        val result = TxBuilder().calculate()
        assertThat(result).isEqualTo(com.tiffles.blockgains.domain.model.CalcResult.EMPTY)
    }

    @Test
    fun shortfall_namesTheRecordsWallet_andSaysWhetherLotsWerePooled() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100", wallet = "FAKE-WALLET-1")
            sell("2024-06-01", qty = "3", price = "150", wallet = "FAKE-WALLET-2")
            sell("2025-06-01", qty = "1", price = "150", wallet = "FAKE-WALLET-2")
        }.calculate()

        val (pre2025, perWallet) = result.issues.filterIsInstance<CalcIssue.InsufficientLots>()
        assertThat(pre2025.wallet).isEqualTo("FAKE-WALLET-2")
        assertThat(pre2025.pooled).isTrue()
        assertThat(perWallet.wallet).isEqualTo("FAKE-WALLET-2")
        assertThat(perWallet.pooled).isFalse()
    }

    @Test
    fun buysOnly_noEventsAndLotsRemain() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100")
            buy("2023-02-01", qty = "2", price = "110")
        }.calculate()

        assertThat(result.taxableEvents).isEmpty()
        assertThat(result.remainingLots.sumOf { it.quantity }).isEquivalentAccordingToCompareTo(BigDecimal("3"))
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun manyPartialLots_matchInOrderAndConserveBasis() {
        val builder = TxBuilder().apply {
            repeat(100) { i -> buy("2023-01-${"%02d".format(1 + i % 28)}", qty = "0.01", price = "${100 + i}") }
            sell("2023-12-01", qty = "0.555", price = "500")
        }
        val result = builder.calculate()

        val event = result.taxableEvents.single()
        assertThat(event.lots).hasSize(56)
        assertExact(event.quantity, "0.555")
        assertThat(event.lots.zipWithNext().all { (a, b) -> !a.acquiredDate!!.isAfter(b.acquiredDate) }).isTrue()
        val totalBasis = builder.transactions.sumOf { if (it.type == TransactionType.BUY) it.usdValue!! else BigDecimal.ZERO }
        assertThat(event.basis + result.remainingLots.sumOf { it.basis }).isEquivalentAccordingToCompareTo(totalBasis)
        assertExact(event.proceeds, "277.5")
    }

    @Test
    fun nineDecimalQuantities_keepFullPrecision() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "0.123456789", price = "30000")
            sell("2023-06-01", qty = "0.000000001", price = "40000")
        }.calculate()

        val lot = result.taxableEvents.single().lots.single()
        assertExact(lot.basis, "0.00003")
        assertExact(lot.proceeds, "0.00004")
        assertExact(result.remainingLots.single().quantity, "0.123456788")
        assertExact(result.remainingLots.single().basis, "3703.70364") // 3703.70367 − 0.00003
    }

    @Test
    fun partiallyShortDisposal_unmatchedUnitsAreZeroBasisShortTerm() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100")
            sell("2023-06-01", qty = "1.5", price = "300", fee = "3")
        }.calculate()

        val event = result.taxableEvents.single()
        assertExact(event.quantity, "1.5")
        assertExact(event.proceeds, "447")
        assertExact(event.fee, "3")
        val (matched, unmatched) = event.lots
        assertExact(matched.proceeds, "298") // (450 − 3) × 1 / 1.5
        assertExact(matched.fee, "2")
        assertExact(unmatched.quantity, "0.5")
        assertExact(unmatched.basis, "0")
        assertExact(unmatched.proceeds, "149")
        assertExact(unmatched.fee, "1")
        assertThat(unmatched.basisUnknown).isTrue()
        assertThat(unmatched.term).isEqualTo(HoldingTerm.SHORT)
        assertThat(unmatched.acquiredDate).isNull()
        assertThat(matched.acquiredDate).isEqualTo(d("2023-01-01"))
        assertThat(unmatched.sourceTransactionId).isEqualTo(event.transactionId)
        val issue = result.issues.single() as CalcIssue.InsufficientLots
        assertExact(issue.requested, "1.5")
        assertExact(issue.available, "1")
        assertExact(issue.shortBy, "0.5")
    }

    @Test
    fun invalidRow_isReportedAndOtherRowsStillCalculate() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100")
            raw(Transaction(type = TransactionType.SELL, date = d("2023-02-01"), asset = "FAKEA", wallet = "FAKE-WALLET-1", quantity = BigDecimal.ONE))
            sell("2023-03-01", qty = "1", price = "150")
        }.calculate()

        assertCents(result.taxableEvents.single().gain, "50")
        val issue = result.issues.single() as CalcIssue.InvalidRow
        assertThat(issue.reason).isEqualTo(InvalidReason.MISSING_VALUE)
        assertThat(issue.transactionId).isEqualTo(2L)
    }

    @Test
    fun sameDay_acquisitionIsProcessedBeforeDisposal() {
        val result = TxBuilder().apply {
            sell("2023-05-05", qty = "1", price = "150")
            buy("2023-05-05", qty = "1", price = "100")
        }.calculate()

        assertCents(result.taxableEvents.single().gain, "50")
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun sameDay_swapThenTransfer_followsInputOrder() {
        val result = TxBuilder().apply {
            buy("2025-01-02", qty = "1", price = "100", asset = "FAKEB", wallet = "FAKE-WALLET-2")
            swap("2025-03-10", qty = "1", asset = "FAKEB", toAsset = "FAKEC", toQty = "4", value = "200", wallet = "FAKE-WALLET-2")
            transfer("2025-03-10", qty = "4", from = "FAKE-WALLET-2", to = "FAKE-EXCHANGE-1", asset = "FAKEC")
        }.calculate()

        assertThat(result.issues).isEmpty()
        assertThat(result.remainingLots.single { it.asset == "FAKEC" }.wallet).isEqualTo("FAKE-EXCHANGE-1")
    }

    @Test
    fun sameDay_transferListedBeforeSwap_runsFirst() {
        val result = TxBuilder().apply {
            buy("2025-01-02", qty = "1", price = "100", asset = "FAKEB", wallet = "FAKE-WALLET-2")
            transfer("2025-03-10", qty = "4", from = "FAKE-WALLET-2", to = "FAKE-EXCHANGE-1", asset = "FAKEC")
            swap("2025-03-10", qty = "1", asset = "FAKEB", toAsset = "FAKEC", toQty = "4", value = "200", wallet = "FAKE-WALLET-2")
        }.calculate()

        val issue = result.issues.single() as CalcIssue.InsufficientLots
        assertThat(issue.type).isEqualTo(TransactionType.TRANSFER)
        assertThat(result.remainingLots.single { it.asset == "FAKEC" }.wallet).isEqualTo("FAKE-WALLET-2")
    }

    @Test
    fun swapFee_reducesAmountRealized_notNewBasis() {
        val result = TxBuilder().apply {
            buy("2025-01-02", qty = "1", price = "100", asset = "FAKEB", wallet = "FAKE-WALLET-1")
            swap("2025-02-01", qty = "1", asset = "FAKEB", toAsset = "FAKEUSD", toQty = "150", value = "150", wallet = "FAKE-WALLET-1", fee = "5")
        }.calculate()

        assertCents(result.taxableEvents.single().gain, "45") // 150 − 5 − 100
        assertExact(result.remainingLots.single { it.asset == "FAKEUSD" }.basis, "150")
    }

    @Test
    fun transferBefore2025_isIgnoredForMatching() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100", wallet = "FAKE-WALLET-1")
            transfer("2023-06-01", qty = "1", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1")
            sell("2023-07-01", qty = "1", price = "120", wallet = "FAKE-WALLET-2")
        }.calculate()

        assertThat(result.taxableEvents.single().scope).isEqualTo(MatchingScope.UNIVERSAL)
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun transfer_movesOldestLotsFirst_keepingTheirDatesAndBasis() { // R5
        val result = TxBuilder().apply {
            buy("2025-01-02", qty = "1", price = "100", wallet = "FAKE-WALLET-1")
            buy("2025-01-03", qty = "1", price = "200", wallet = "FAKE-WALLET-1")
            transfer("2025-02-01", qty = "1.5", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1")
        }.calculate()

        val moved = result.remainingLots.filter { it.wallet == "FAKE-EXCHANGE-1" }
        assertThat(moved.map { it.acquiredDate }).containsExactly(d("2025-01-02"), d("2025-01-03")).inOrder()
        assertExact(moved.sumOf { it.basis }, "200") // 100 + half of 200
        val left = result.remainingLots.single { it.wallet == "FAKE-WALLET-1" }
        assertThat(left.acquiredDate).isEqualTo(d("2025-01-03"))
        assertExact(left.quantity, "0.5")
        assertThat(result.taxableEvents).isEmpty() // no gain or loss
    }

    @Test
    fun transferredInOlderLot_sortsByOriginalDate_andIsSoldFirst() { // R3, Treas. Reg. 1.1012-1(j)(3)(i)
        val result = TxBuilder().apply {
            buy("2025-01-02", qty = "1", price = "100", wallet = "FAKE-WALLET-1") // older lot, other wallet
            buy("2025-03-01", qty = "1", price = "300", wallet = "FAKE-EXCHANGE-1") // newer lot, already here
            transfer("2025-04-01", qty = "1", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1") // older lot arrives later
            sell("2025-05-01", qty = "1", price = "400", wallet = "FAKE-EXCHANGE-1")
        }.calculate()

        val lot = result.taxableEvents.single().lots.single()
        assertThat(lot.acquiredDate).isEqualTo(d("2025-01-02"))
        assertExact(lot.basis, "100")
        assertThat(result.remainingLots.single().acquiredDate).isEqualTo(d("2025-03-01"))
    }

    @Test
    fun from2025_sellInWalletWithoutLots_isShortEvenIfAnotherWalletHasThem() {
        val result = TxBuilder().apply {
            buy("2025-02-01", qty = "1", price = "100", wallet = "FAKE-WALLET-1")
            sell("2025-03-01", qty = "1", price = "120", wallet = "FAKE-EXCHANGE-1")
        }.calculate()

        val lot = result.taxableEvents.single().lots.single()
        assertExact(lot.basis, "0")
        assertThat(lot.wallet).isEqualTo("FAKE-EXCHANGE-1")
        val issue = result.issues.single() as CalcIssue.InsufficientLots
        assertThat(issue.wallet).isEqualTo("FAKE-EXCHANGE-1")
        assertThat(result.remainingLots.single().wallet).isEqualTo("FAKE-WALLET-1")
    }

    @Test
    fun noOpeningRecords_universalRemainderMovesToDefaultWallet() {
        val result = TxBuilder().apply {
            buy("2022-01-01", qty = "2", price = "100", wallet = "FAKE-WALLET-1")
            sell("2025-06-01", qty = "1", price = "300", wallet = "FAKE-EXCHANGE-1")
        }.calculate(defaultWallet = "FAKE-EXCHANGE-1")

        assertCents(result.taxableEvents.single().gain, "200")
        assertThat(result.remainingLots.single().wallet).isEqualTo("FAKE-EXCHANGE-1")
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun openingForAssetWithNoHistory_doesNotWarn() {
        val result = TxBuilder().apply {
            opening("FAKEC", "FAKE-WALLET-1", qty = "10", acquired = "2021-01-01", basis = "100")
            buy("2025-02-01", qty = "1", price = "50", asset = "FAKEC") // 2025+ records aren't history
        }.calculate()

        assertThat(result.issues).isEmpty()
        assertThat(result.remainingLots.map { it.wallet }).containsExactly("FAKE-WALLET-1", "FAKE-WALLET-1")
    }

    @Test
    fun openingForAssetWhosePreTransitionHistoryIsAllSold_reportsMismatch() {
        val result = TxBuilder().apply {
            buy("2022-01-01", qty = "1", price = "100", asset = "FAKEC")
            sell("2023-01-01", qty = "1", price = "120", asset = "FAKEC")
            opening("FAKEC", "FAKE-WALLET-1", qty = "10", acquired = "2021-01-01", basis = "100")
        }.calculate()

        val issue = result.issues.single() as CalcIssue.TransitionMismatch
        assertExact(issue.expected, "0")
        assertExact(issue.opening, "10")
    }

    @Test
    fun openingForAssetOnlyReceivedInPreTransitionSwap_reportsMismatch() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100", asset = "FAKEB")
            swap("2023-02-01", qty = "1", asset = "FAKEB", toAsset = "FAKEUSD", toQty = "120", value = "120", wallet = "FAKE-WALLET-1")
            opening("FAKEUSD", "FAKE-WALLET-1", qty = "100", acquired = "2023-02-01", basis = "100")
        }.calculate()

        val issue = result.issues.single() as CalcIssue.TransitionMismatch
        assertThat(issue.asset).isEqualTo("FAKEUSD")
        assertExact(issue.expected, "120")
        assertExact(issue.opening, "100")
    }

    /** R4: only a SWAP's `to_asset` counts as history; a stray one on another type is ignored (1.8.1). */
    @Test
    fun strayToAssetOnPreTransitionBuy_isNotHistory_soNoMismatch() {
        val builder = TxBuilder().apply {
            raw(
                Transaction(
                    type = TransactionType.BUY, date = d("2024-03-01"), asset = "FAKEA", wallet = "FAKE-WALLET-1",
                    quantity = BigDecimal("1"), priceUsd = BigDecimal("100"), toAsset = "FAKEB",
                ),
            )
            opening("FAKEA", "FAKE-WALLET-1", qty = "1", acquired = "2024-03-01", basis = "100")
            opening("FAKEB", "FAKE-WALLET-1", qty = "5", acquired = "2024-03-01", basis = "50")
        }

        assertThat(builder.calculate().issues.filterIsInstance<CalcIssue.TransitionMismatch>()).isEmpty()
        assertThat(TaxRules.assetsWithHistory(builder.transactions)).containsExactly("FAKEA")
    }

    @Test
    fun matchingStrategy_isPluggable() {
        val newestFirst = LotMatchingStrategy { pool, quantity, _ ->
            var needed = quantity
            pool.indices.reversed().mapNotNull { i ->
                if (needed.signum() <= 0) return@mapNotNull null
                val take = pool[i].quantity.min(needed)
                needed -= take
                LotTake(i, take)
            }
        }
        val txs = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100")
            buy("2023-02-01", qty = "1", price = "200")
            sell("2023-03-01", qty = "1", price = "300")
        }.transactions

        val event = TaxCalculator(newestFirst).calculate(txs, CalcSettings("FAKE-WALLET-1")).taxableEvents.single()
        assertCents(event.gain, "100")
    }

    @Test
    fun fifoStrategy_skipsLotsAcquiredAfterTheDisposal() {
        val lot = { date: String -> Lot(1, "FAKEA", com.tiffles.blockgains.domain.model.AssetClass.CRYPTO, "W", d(date), BigDecimal.ONE, BigDecimal.TEN, false) }
        val takes = FifoStrategy.select(listOf(lot("2023-01-01"), lot("2023-09-01")), BigDecimal("2"), d("2023-06-01"))
        assertThat(takes).containsExactly(LotTake(0, BigDecimal.ONE))
    }
}
