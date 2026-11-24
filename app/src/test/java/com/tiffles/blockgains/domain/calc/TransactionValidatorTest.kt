package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import org.junit.Test

class TransactionValidatorTest {

    private val buy = Transaction(
        type = TransactionType.BUY, date = d("2023-01-01"), asset = "FAKEA", wallet = "FAKE-WALLET-1",
        quantity = BigDecimal.ONE, priceUsd = BigDecimal("100"),
    )

    private fun reason(tx: Transaction) = TransactionValidator.validate(tx)

    @Test
    fun validBuy_passes() {
        assertThat(reason(buy)).isNull()
    }

    @Test
    fun quantityRules() {
        assertThat(reason(buy.copy(quantity = BigDecimal.ZERO))).isEqualTo(InvalidReason.QUANTITY_NOT_POSITIVE)
        val adjust = buy.copy(type = TransactionType.ADJUST, priceUsd = null)
        assertThat(reason(adjust)).isEqualTo(InvalidReason.ADJUST_NOT_NEGATIVE)
        assertThat(reason(adjust.copy(quantity = BigDecimal("-0.1")))).isNull()
    }

    @Test
    fun valueRules() {
        assertThat(reason(buy.copy(priceUsd = null))).isEqualTo(InvalidReason.MISSING_VALUE)
        assertThat(reason(buy.copy(priceUsd = BigDecimal("-1")))).isEqualTo(InvalidReason.NEGATIVE_VALUE)
        assertThat(reason(buy.copy(totalUsd = BigDecimal("100.01")))).isNull()
        assertThat(reason(buy.copy(totalUsd = BigDecimal("100.02")))).isEqualTo(InvalidReason.PRICE_TOTAL_MISMATCH)
    }

    @Test
    fun feeRules() {
        assertThat(reason(buy.copy(feeUsd = BigDecimal("-1")))).isEqualTo(InvalidReason.NEGATIVE_FEE)
        val income = buy.copy(type = TransactionType.INCOME, feeUsd = BigDecimal("1"))
        assertThat(reason(income)).isEqualTo(InvalidReason.FEE_NOT_ALLOWED)
        assertThat(reason(income.copy(feeUsd = BigDecimal.ZERO))).isNull()
    }

    @Test
    fun networkFeeRules_r13() {
        val transfer = buy.copy(type = TransactionType.TRANSFER, priceUsd = null, toWallet = "FAKE-EXCHANGE-1")
        val withFee = transfer.copy(feeQuantity = BigDecimal("0.01"), feeUsd = BigDecimal("3"))
        assertThat(reason(withFee)).isNull()
        assertThat(reason(withFee.copy(feeAsset = "FAKEB"))).isNull()
        assertThat(reason(withFee.copy(feeUsd = BigDecimal.ZERO))).isNull() // worthless fee coins are still a disposal
        assertThat(reason(transfer.copy(feeUsd = BigDecimal.ZERO))).isNull() // $0 alone = no fee, as before R13

        assertThat(reason(withFee.copy(feeUsd = null))).isEqualTo(InvalidReason.NETWORK_FEE_MISSING_VALUE)
        assertThat(reason(transfer.copy(feeUsd = BigDecimal("3")))).isEqualTo(InvalidReason.NETWORK_FEE_MISSING_QUANTITY)
        assertThat(reason(transfer.copy(feeAsset = "FAKEB"))).isEqualTo(InvalidReason.NETWORK_FEE_MISSING_QUANTITY)
        assertThat(reason(withFee.copy(feeQuantity = BigDecimal.ZERO))).isEqualTo(InvalidReason.NETWORK_FEE_QUANTITY_NOT_POSITIVE)
        assertThat(reason(withFee.copy(feeQuantity = BigDecimal("-0.01")))).isEqualTo(InvalidReason.NETWORK_FEE_QUANTITY_NOT_POSITIVE)
        assertThat(reason(withFee.copy(feeUsd = BigDecimal("-1")))).isEqualTo(InvalidReason.NEGATIVE_FEE)

        assertThat(reason(buy.copy(feeQuantity = BigDecimal("0.01")))).isEqualTo(InvalidReason.NETWORK_FEE_NOT_ALLOWED)
        assertThat(reason(buy.copy(feeAsset = "FAKEB"))).isEqualTo(InvalidReason.NETWORK_FEE_NOT_ALLOWED)
    }

    @Test
    fun swapRules() {
        val swap = buy.copy(type = TransactionType.SWAP, priceUsd = null, totalUsd = BigDecimal("10"), toAsset = "FAKEUSD", toQuantity = BigDecimal("10"))
        assertThat(reason(swap)).isNull()
        assertThat(reason(swap.copy(toAsset = null))).isEqualTo(InvalidReason.SWAP_MISSING_TO_ASSET)
        assertThat(reason(swap.copy(toAsset = "FAKEA"))).isEqualTo(InvalidReason.SWAP_SAME_ASSET)
        assertThat(reason(swap.copy(toQuantity = null))).isEqualTo(InvalidReason.SWAP_MISSING_TO_QUANTITY)
        assertThat(reason(swap.copy(totalUsd = null))).isEqualTo(InvalidReason.MISSING_VALUE)
    }

    @Test
    fun transferRules() {
        val transfer = buy.copy(type = TransactionType.TRANSFER, priceUsd = null, toWallet = "FAKE-EXCHANGE-1")
        assertThat(reason(transfer)).isNull()
        assertThat(reason(transfer.copy(toWallet = null))).isEqualTo(InvalidReason.TRANSFER_MISSING_TO_WALLET)
        assertThat(reason(transfer.copy(toWallet = "FAKE-WALLET-1"))).isEqualTo(InvalidReason.TRANSFER_SAME_WALLET)
    }

    @Test
    fun openingRules() {
        val opening = buy.copy(type = TransactionType.OPENING, date = TaxRules.TRANSITION_DATE, acquiredDate = d("2020-01-01"))
        assertThat(reason(opening)).isNull()
        assertThat(reason(opening.copy(date = d("2025-01-02")))).isEqualTo(InvalidReason.OPENING_WRONG_DATE)
        assertThat(reason(opening.copy(acquiredDate = null))).isEqualTo(InvalidReason.OPENING_MISSING_ACQUIRED_DATE)
        assertThat(reason(opening.copy(acquiredDate = d("2025-02-01")))).isEqualTo(InvalidReason.OPENING_ACQUIRED_AFTER_TRANSITION)
    }

    @Test
    fun blankLabels() {
        assertThat(reason(buy.copy(asset = " "))).isEqualTo(InvalidReason.BLANK_ASSET)
        assertThat(reason(buy.copy(wallet = ""))).isEqualTo(InvalidReason.BLANK_WALLET)
    }
}
