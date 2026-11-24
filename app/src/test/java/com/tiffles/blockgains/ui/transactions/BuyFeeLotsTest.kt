package com.tiffles.blockgains.ui.transactions

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.TxBuilder
import org.junit.Test

/** "Includes buy fee" under Basis on event detail (R7). All numbers are made up. */
class BuyFeeLotsTest {

    @Test
    fun onlyLotsFromABuyWithAFee_evenAfterATransfer() {
        val builder = TxBuilder()
        val withFee = builder.buy("2025-01-10", qty = "1", price = "100", fee = "2", asset = "FAKEB", wallet = "FAKE-WALLET-1")
        val zeroFee = builder.buy("2025-01-11", qty = "1", price = "100", fee = "0", asset = "FAKEB", wallet = "FAKE-WALLET-1")
        val noFee = builder.buy("2025-01-12", qty = "1", price = "100", asset = "FAKEB", wallet = "FAKE-WALLET-1")
        builder.transfer("2025-02-01", qty = "3", from = "FAKE-WALLET-1", to = "FAKE-EXCHANGE-1", asset = "FAKEB")
        builder.sell("2025-03-01", qty = "4", price = "150", asset = "FAKEB", wallet = "FAKE-EXCHANGE-1") // 1 unit short: $0-basis slice

        val event = builder.calculate().taxableEvents.single()

        assertThat(event.lots.map { it.sourceTransactionId }).containsAtLeast(withFee.id, zeroFee.id, noFee.id)
        assertThat(buyFeeLots(builder.transactions, event)).containsExactly(withFee.id)
    }
}
