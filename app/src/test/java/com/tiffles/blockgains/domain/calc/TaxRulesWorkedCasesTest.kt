package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.MatchingScope
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import org.junit.Test

/** Every worked case in `tax-rules.md`, named after it. All numbers are made up. */
class TaxRulesWorkedCasesTest {

    @Test
    fun T1_anniversaryIsShortTerm() {
        val onAnniversary = TxBuilder().apply {
            buy("2023-03-01", qty = "1", price = "100")
            sell("2024-03-01", qty = "1", price = "150")
        }.calculate().taxableEvents.single()
        assertThat(onAnniversary.term).isEqualTo(HoldingTerm.SHORT)
        assertCents(onAnniversary.shortTermGain, "50.00")

        val dayAfter = TxBuilder().apply {
            buy("2023-03-01", qty = "1", price = "100")
            sell("2024-03-02", qty = "1", price = "150")
        }.calculate().taxableEvents.single()
        assertThat(dayAfter.term).isEqualTo(HoldingTerm.LONG)
        assertCents(dayAfter.longTermGain, "50.00")
    }

    @Test
    fun T2_leapDay() {
        fun termWhenSoldOn(date: String) = TxBuilder().apply {
            buy("2024-02-29", qty = "1", price = "100")
            sell(date, qty = "1", price = "150")
        }.calculate().taxableEvents.single().term

        assertThat(termWhenSoldOn("2025-02-28")).isEqualTo(HoldingTerm.SHORT)
        assertThat(termWhenSoldOn("2025-03-01")).isEqualTo(HoldingTerm.LONG)
    }

    @Test
    fun T3_fifoAcrossLotsWithFees() {
        val result = TxBuilder().apply {
            buy("2022-01-10", qty = "2", price = "100", fee = "2")
            buy("2022-06-01", qty = "1", price = "200")
            sell("2023-03-01", qty = "2.5", price = "300", fee = "3")
        }.calculate()

        val event = result.taxableEvents.single()
        val (lot1, lot2) = event.lots
        assertExact(lot1.quantity, "2")
        assertExact(lot1.proceeds, "597.60")
        assertExact(lot1.basis, "202.00")
        assertThat(lot1.term).isEqualTo(HoldingTerm.LONG)
        assertExact(lot1.gain, "395.60")

        assertExact(lot2.quantity, "0.5")
        assertExact(lot2.proceeds, "149.40")
        assertExact(lot2.basis, "100.00")
        assertThat(lot2.term).isEqualTo(HoldingTerm.SHORT)
        assertExact(lot2.gain, "49.40")

        assertExact(event.gain, "445.00")
        assertThat(event.term).isNull() // mixed
        val remaining = result.remainingLots.single()
        assertExact(remaining.quantity, "0.5")
        assertExact(remaining.basis, "100")
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun T4_noMatchingFutureLots() {
        val result = TxBuilder().apply {
            buy("2023-02-01", qty = "1", price = "100")
            sell("2023-01-01", qty = "1", price = "150")
        }.calculate()

        // Open questions, option B: the unmatched unit is a $0-basis, short-term sale.
        val lot = result.taxableEvents.single().lots.single()
        assertExact(lot.quantity, "1")
        assertExact(lot.basis, "0")
        assertThat(lot.basisUnknown).isTrue()
        assertThat(lot.term).isEqualTo(HoldingTerm.SHORT)
        assertThat(lot.acquiredDate).isNull()
        assertCents(lot.gain, "150.00")
        val issue = result.issues.single() as CalcIssue.InsufficientLots
        assertThat(issue.date).isEqualTo(d("2023-01-01"))
        assertExact(issue.shortBy, "1")
        assertExact(result.remainingLots.single().quantity, "1")
    }

    @Test
    fun T5_perWalletFifoFrom2025() {
        val result = TxBuilder().apply {
            buy("2018-01-01", qty = "1", price = "15000", wallet = "FAKE-WALLET-1")
            buy("2020-01-01", qty = "1", price = "10000", wallet = "FAKE-EXCHANGE-1")
            opening("FAKEA", "FAKE-EXCHANGE-1", qty = "1", acquired = "2020-01-01", basis = "10000")
            opening("FAKEA", "FAKE-WALLET-1", qty = "1", acquired = "2018-01-01", basis = "15000")
            sell("2025-06-01", qty = "1", price = "60000", wallet = "FAKE-EXCHANGE-1")
        }.calculate()

        val event = result.taxableEvents.single()
        assertThat(event.scope).isEqualTo(MatchingScope.PER_WALLET)
        assertThat(event.lots.single().acquiredDate).isEqualTo(d("2020-01-01"))
        assertThat(event.term).isEqualTo(HoldingTerm.LONG)
        assertCents(event.gain, "50000.00") // universal FIFO would wrongly give 45,000
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun T6_transferKeepsOriginalDate() {
        val result = TxBuilder().apply {
            buy("2024-11-01", qty = "2", price = "3000", asset = "FAKEB", wallet = "FAKE-WALLET-1")
            opening("FAKEB", "FAKE-WALLET-1", qty = "2", acquired = "2024-11-01", basis = "6000")
            transfer("2025-03-01", qty = "1", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEB")
            sell("2025-06-01", qty = "1", price = "4000", asset = "FAKEB", wallet = "FAKE-EXCHANGE-1")
        }.calculate()

        val event = result.taxableEvents.single()
        assertThat(event.lots.single().acquiredDate).isEqualTo(d("2024-11-01"))
        assertThat(event.term).isEqualTo(HoldingTerm.SHORT)
        assertCents(event.gain, "1000.00")
        val left = result.remainingLots.single()
        assertThat(left.wallet).isEqualTo("FAKE-WALLET-1")
        assertExact(left.quantity, "1")
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun T7_swapThenTransferThenSell() {
        val result = TxBuilder().apply {
            buy("2023-01-05", qty = "10", price = "20", asset = "FAKEB", wallet = "FAKE-WALLET-2")
            opening("FAKEB", "FAKE-WALLET-2", qty = "10", acquired = "2023-01-05", basis = "200")
            swap("2025-03-10", qty = "10", asset = "FAKEB", toAsset = "FAKEUSD", toQty = "1500", value = "1500", wallet = "FAKE-WALLET-2")
            transfer("2025-03-12", qty = "1500", from = "FAKE-WALLET-2", to = "FAKE-EXCHANGE-1", asset = "FAKEUSD")
            sell("2025-03-15", qty = "1500", price = "1.00", asset = "FAKEUSD", wallet = "FAKE-EXCHANGE-1")
        }.calculate()

        val (swapOut, fakeUsdSale) = result.taxableEvents
        assertThat(swapOut.asset).isEqualTo("FAKEB")
        assertThat(swapOut.wallet).isEqualTo("FAKE-WALLET-2")
        assertThat(swapOut.receivedAsset).isEqualTo("FAKEUSD")
        assertThat(swapOut.term).isEqualTo(HoldingTerm.LONG)
        assertCents(swapOut.gain, "1300.00")

        val fakeUsdLot = fakeUsdSale.lots.single()
        assertThat(fakeUsdLot.acquiredDate).isEqualTo(d("2025-03-10"))
        assertExact(fakeUsdLot.basis, "1500")
        assertThat(fakeUsdSale.term).isEqualTo(HoldingTerm.SHORT)
        assertCents(fakeUsdSale.gain, "0.00")
        assertThat(result.remainingLots).isEmpty()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun T8_incomeCreatesBasis() {
        val result = TxBuilder().apply {
            income("2025-02-01", qty = "0.5", price = "100", asset = "FAKEB", wallet = "FAKE-EXCHANGE-1")
            sell("2025-04-01", qty = "0.5", price = "120", asset = "FAKEB", wallet = "FAKE-EXCHANGE-1")
        }.calculate()

        assertCents(result.incomeEvents.single().valueUsd, "50.00")
        val event = result.taxableEvents.single()
        assertExact(event.basis, "50")
        assertThat(event.term).isEqualTo(HoldingTerm.SHORT)
        assertCents(event.gain, "10.00")
    }

    @Test
    fun T9_adjustmentHasNoTaxEffect() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "1000", asset = "FAKEB")
            adjust("2023-06-01", qty = "-0.001", asset = "FAKEB")
            sell("2024-06-01", qty = "0.999", price = "2000", asset = "FAKEB")
        }.calculate()

        val event = result.taxableEvents.single()
        assertExact(event.quantity, "0.999")
        assertExact(event.basis, "999.00")
        assertExact(event.proceeds, "1998.00")
        assertThat(event.term).isEqualTo(HoldingTerm.LONG)
        assertExact(event.gain, "999.00")
        assertExact(result.adjustments.single().quantity, "0.001")
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun T10_unknownBasis() {
        fun run(basisUnknown: Boolean) = TxBuilder().apply {
            buy("2022-07-15", qty = "1.5", price = "0", asset = "FAKEB", basisUnknown = basisUnknown)
            sell("2024-03-01", qty = "1.5", price = "100", asset = "FAKEB")
        }.calculate()

        val unknown = run(basisUnknown = true)
        assertThat(unknown.taxableEvents.single().term).isEqualTo(HoldingTerm.LONG)
        assertCents(unknown.taxableEvents.single().gain, "150.00")
        assertThat(unknown.taxableEvents.single().lots.single().basisUnknown).isTrue()
        assertThat(unknown.issues).isEmpty()

        val unconfirmed = run(basisUnknown = false)
        assertCents(unconfirmed.taxableEvents.single().gain, "150.00")
        assertThat(unconfirmed.issues.single()).isInstanceOf(CalcIssue.ZeroPriceBuy::class.java)
    }

    @Test
    fun T11_transitionMismatch() {
        val result = TxBuilder().apply {
            buy("2023-04-01", qty = "5.000", price = "1000", asset = "FAKEB")
            opening("FAKEB", "FAKE-WALLET-1", qty = "3", acquired = "2023-04-01", basis = "3000")
            opening("FAKEB", "FAKE-WALLET-2", qty = "1.990", acquired = "2023-04-01", basis = "1990")
        }.calculate()

        val issue = result.issues.single() as CalcIssue.TransitionMismatch
        assertThat(issue.asset).isEqualTo("FAKEB")
        assertExact(issue.expected, "5.000")
        assertExact(issue.opening, "4.990")
        assertThat(result.remainingLots.map { it.wallet to it.quantity.toPlainString() })
            .containsExactly("FAKE-WALLET-1" to "3", "FAKE-WALLET-2" to "1.990")
        assertExact(result.universalLotsAtTransition.single().quantity, "5")
    }

    @Test
    fun T12_yearTotalsAndNetting() {
        val result = TxBuilder().apply {
            // 2023: ST −5,000 and LT +1,000
            buy("2023-01-02", qty = "1", price = "6000", asset = "FAKEA")
            sell("2023-06-01", qty = "1", price = "1000", asset = "FAKEA")
            buy("2021-01-01", qty = "1", price = "1000", asset = "FAKEB")
            sell("2023-06-01", qty = "1", price = "2000", asset = "FAKEB")
            // 2024: ST −400 and LT +1,000
            buy("2024-01-02", qty = "1", price = "1000", asset = "FAKEC")
            sell("2024-05-01", qty = "1", price = "600", asset = "FAKEC")
            buy("2022-01-01", qty = "1", price = "500", asset = "FAKED")
            sell("2024-02-01", qty = "1", price = "1500", asset = "FAKED")
        }.calculate()
        val summarize = SummarizeYearUseCase()

        val y2024 = summarize(result, 2024, AssetFilter.ALL)
        assertCents(y2024.shortTerm, "-400.00")
        assertCents(y2024.longTerm, "1000.00")
        assertCents(y2024.net, "600.00")
        assertThat(y2024.showsLossLimitNote).isFalse()

        val y2023 = summarize(result, 2023, AssetFilter.ALL)
        assertCents(y2023.shortTerm, "-5000.00")
        assertCents(y2023.longTerm, "1000.00")
        assertCents(y2023.net, "-4000.00")
        assertThat(y2023.showsLossLimitNote).isTrue()
    }

    @Test
    fun T13_transferFeeInTheSameCoin() {
        val result = TxBuilder().apply {
            opening("FAKEB", "FAKE-WALLET-1", qty = "1", acquired = "2024-01-10", basis = "2000")
            transfer("2025-04-01", qty = "0.99", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEB", feeQty = "0.01", feeUsd = "30.00")
        }.calculate()

        val fee = result.taxableEvents.single()
        assertThat(fee.isTransferFee).isTrue()
        assertThat(fee.wallet).isEqualTo("FAKE-WALLET-1")
        assertExact(fee.quantity, "0.01")
        assertExact(fee.proceeds, "30.00")
        assertExact(fee.basis, "20")
        assertExact(fee.fee, "0")
        assertThat(fee.lots.single().acquiredDate).isEqualTo(d("2024-01-10"))
        assertThat(fee.term).isEqualTo(HoldingTerm.LONG)
        assertCents(fee.gain, "10.00")
        val moved = result.remainingLots.single()
        assertThat(moved.wallet).isEqualTo("FAKE-EXCHANGE-1")
        assertExact(moved.quantity, "0.99")
        assertExact(moved.basis, "1980")
        assertThat(moved.acquiredDate).isEqualTo(d("2024-01-10"))
        assertThat(result.issues).isEmpty()

        val withSale = TxBuilder().apply {
            opening("FAKEB", "FAKE-WALLET-1", qty = "1", acquired = "2024-01-10", basis = "2000")
            transfer("2025-04-01", qty = "0.99", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEB", feeQty = "0.01", feeUsd = "30.00")
            sell("2025-06-01", qty = "0.99", price = "3100", asset = "FAKEB", wallet = "FAKE-EXCHANGE-1")
        }.calculate()
        val sale = withSale.taxableEvents.single { !it.isTransferFee }
        assertExact(sale.proceeds, "3069.00")
        assertExact(sale.basis, "1980")
        assertCents(sale.longTermGain, "1089.00")
        assertCents(SummarizeYearUseCase()(withSale, 2025, AssetFilter.ALL).longTerm, "1099.00")
        assertThat(withSale.remainingLots).isEmpty()
        assertThat(withSale.issues).isEmpty()
    }

    @Test
    fun T14_feeInAnotherCoinBefore2025AcrossTwoLots() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "0.004", price = "1000", asset = "FAKEB")
            buy("2023-11-01", qty = "1", price = "2000", asset = "FAKEB")
            buy("2023-01-01", qty = "100", price = "1.00", asset = "FAKEUSD")
            transfer(
                "2024-03-01", qty = "100", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEUSD",
                feeQty = "0.005", feeUsd = "15.00", feeAsset = "FAKEB",
            )
        }.calculate()

        val fee = result.taxableEvents.single()
        assertThat(fee.asset).isEqualTo("FAKEB")
        assertThat(fee.scope).isEqualTo(MatchingScope.UNIVERSAL)
        val (old, newer) = fee.lots
        assertExact(old.quantity, "0.004")
        assertExact(old.proceeds, "12.00")
        assertExact(old.basis, "4")
        assertThat(old.term).isEqualTo(HoldingTerm.LONG)
        assertCents(old.gain, "8.00")
        assertExact(newer.quantity, "0.001")
        assertExact(newer.proceeds, "3.00")
        assertExact(newer.basis, "2")
        assertThat(newer.term).isEqualTo(HoldingTerm.SHORT)
        assertCents(newer.gain, "1.00")

        val left = result.remainingLots.associateBy { it.asset }
        assertExact(left.getValue("FAKEB").quantity, "0.999")
        assertExact(left.getValue("FAKEB").basis, "1998")
        assertExact(left.getValue("FAKEUSD").quantity, "100")
        assertThat(result.issues).isEmpty()
    }
}
