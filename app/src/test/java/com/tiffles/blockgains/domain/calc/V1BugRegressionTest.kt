package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.HoldingTerm
import org.junit.Test

/**
 * The v1 calculator bugs listed in the v2 spec (1, 2, 3, 5, 6). Bug 4 (CSV parsing) is
 * covered by CsvReaderTest; bug 7 (Context in ViewModel, no DI/persistence) is architectural.
 */
class V1BugRegressionTest {

    @Test
    fun bug1_eachAssetHasItsOwnPool() {
        val result = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "10", asset = "FAKEB")
            buy("2023-02-01", qty = "1", price = "100", asset = "FAKEA")
            sell("2023-03-01", qty = "1", price = "150", asset = "FAKEA")
        }.calculate()

        assertCents(result.taxableEvents.single().gain, "50")
        assertThat(result.remainingLots.single().asset).isEqualTo("FAKEB")
    }

    @Test
    fun bug2_holdingPeriodIsNotDaysOver365() {
        // 2023-03-01 → 2024-03-01 spans Feb 29, so it's 366 days: v1's `daysHeld > 365` called it
        // long-term, but it's exactly one year, which is short-term (R2).
        assertThat(TaxRules.holdingTerm(d("2023-03-01"), d("2024-03-01"))).isEqualTo(HoldingTerm.SHORT)
        assertThat(TaxRules.holdingTerm(d("2023-03-01"), d("2024-03-02"))).isEqualTo(HoldingTerm.LONG)
    }

    @Test
    fun bug3_sellNeverMatchesLaterBuy() {
        val result = TxBuilder().apply {
            buy("2023-06-01", qty = "1", price = "100")
            sell("2023-05-01", qty = "1", price = "150")
        }.calculate()

        // The later buy is never used; the sale counts as $0 basis (tax-rules → Open questions, B).
        val event = result.taxableEvents.single()
        assertThat(event.lots.single().sourceTransactionId).isEqualTo(event.transactionId)
        assertExact(event.basis, "0")
        assertThat(result.issues.single()).isInstanceOf(CalcIssue.InsufficientLots::class.java)
        assertExact(result.remainingLots.single().quantity, "1")
    }

    @Test
    fun bug5_feesAreApplied() {
        val event = TxBuilder().apply {
            buy("2023-01-01", qty = "1", price = "100", fee = "1")
            sell("2023-02-01", qty = "1", price = "150", fee = "2")
        }.calculate().taxableEvents.single()

        assertExact(event.basis, "101")
        assertExact(event.proceeds, "148")
        assertExact(event.gain, "47")
    }

    @Test
    fun bug6_insufficientLotsDoesNotStopTheCalculation() {
        val result = TxBuilder().apply {
            sell("2023-01-01", qty = "5", price = "100", asset = "FAKEB")
            buy("2023-02-01", qty = "1", price = "100")
            sell("2023-03-01", qty = "1", price = "130")
        }.calculate()

        assertCents(result.taxableEvents.single { it.asset == "FAKEA" }.gain, "30")
        assertCents(result.taxableEvents.single { it.asset == "FAKEB" }.gain, "500")
        assertThat(result.issues).hasSize(1)
    }
}
