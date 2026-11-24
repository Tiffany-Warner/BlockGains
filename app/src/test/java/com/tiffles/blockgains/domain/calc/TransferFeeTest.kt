package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.MatchingScope
import org.junit.Test

/** R13 beyond the worked cases (T13/T14): order, shortfalls, before 2025. All numbers are made up. */
class TransferFeeTest {

    @Test
    fun feeIsTakenBeforeTheMovedUnits_oldestLotPaysIt() {
        val result = TxBuilder().apply {
            opening("FAKEB", "FAKE-WALLET-1", qty = "0.01", acquired = "2022-01-01", basis = "10")
            opening("FAKEB", "FAKE-WALLET-1", qty = "1", acquired = "2024-06-01", basis = "2000")
            transfer("2025-03-01", qty = "0.5", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEB", feeQty = "0.01", feeUsd = "25.00")
        }.calculate()

        val fee = result.taxableEvents.single().lots.single()
        assertThat(fee.acquiredDate).isEqualTo(d("2022-01-01"))
        assertThat(fee.term).isEqualTo(HoldingTerm.LONG)
        assertCents(fee.gain, "15.00")
        val moved = result.remainingLots.single { it.wallet == "FAKE-EXCHANGE-1" }
        assertThat(moved.acquiredDate).isEqualTo(d("2024-06-01"))
        assertExact(moved.quantity, "0.5")
        assertExact(moved.basis, "1000")
        assertExact(result.remainingLots.single { it.wallet == "FAKE-WALLET-1" }.quantity, "0.5")
    }

    @Test
    fun feeShortfall_getsAZeroBasisSlice_andMovedShortfallStaysIssueOnly() {
        val result = TxBuilder().apply {
            opening("FAKEB", "FAKE-WALLET-1", qty = "0.004", acquired = "2023-01-01", basis = "8")
            transfer(
                "2025-03-01", qty = "50", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEUSD",
                feeQty = "0.01", feeUsd = "20.00", feeAsset = "FAKEB",
            )
        }.calculate()

        val (matched, unmatched) = result.taxableEvents.single().lots
        assertExact(matched.quantity, "0.004")
        assertExact(matched.proceeds, "8.00")
        assertExact(unmatched.quantity, "0.006")
        assertExact(unmatched.proceeds, "12.00")
        assertExact(unmatched.basis, "0")
        assertThat(unmatched.basisUnknown).isTrue()
        assertThat(unmatched.acquiredDate).isNull()
        assertThat(unmatched.term).isEqualTo(HoldingTerm.SHORT)

        val shortfalls = result.issues.filterIsInstance<CalcIssue.InsufficientLots>()
        assertThat(shortfalls.map { Triple(it.asset, it.networkFee, it.shortBy.stripTrailingZeros().toPlainString()) })
            .containsExactly(Triple("FAKEB", true, "0.006"), Triple("FAKEUSD", false, "50")).inOrder()
    }

    @Test
    fun before2025_theMoveIsIgnored_butTheFeeIsDisposedFromTheUniversalPool() {
        val result = TxBuilder().apply {
            buy("2024-01-05", qty = "1", price = "2000", asset = "FAKEB", wallet = "FAKE-WALLET-1")
            transfer("2024-05-01", qty = "0.49", from = "FAKE-WALLET-1", to = "FAKE-WALLET-2", asset = "FAKEB", feeQty = "0.01", feeUsd = "22.00")
        }.calculate()

        val fee = result.taxableEvents.single()
        assertThat(fee.scope).isEqualTo(MatchingScope.UNIVERSAL)
        assertExact(fee.quantity, "0.01")
        assertCents(fee.gain, "2.00")
        // The move itself doesn't touch the pool (R4): only the fee units left it.
        assertExact(result.remainingLots.single().quantity, "0.99")
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun zeroFeeUsdWithoutFeeQuantity_isNoFee() {
        val result = TxBuilder().apply {
            opening("FAKEB", "FAKE-WALLET-1", qty = "1", acquired = "2024-01-10", basis = "2000")
            transfer("2025-04-01", qty = "1", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEB", feeUsd = "0")
        }.calculate()

        assertThat(result.taxableEvents).isEmpty()
        assertThat(result.issues).isEmpty()
        assertThat(result.remainingLots.single().wallet).isEqualTo("FAKE-EXCHANGE-1")
    }
}
