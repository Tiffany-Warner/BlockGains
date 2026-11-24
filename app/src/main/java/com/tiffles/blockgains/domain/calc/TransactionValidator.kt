package com.tiffles.blockgains.domain.calc

import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.model.TransactionType.ADJUST
import com.tiffles.blockgains.domain.model.TransactionType.BUY
import com.tiffles.blockgains.domain.model.TransactionType.INCOME
import com.tiffles.blockgains.domain.model.TransactionType.OPENING
import com.tiffles.blockgains.domain.model.TransactionType.SELL
import com.tiffles.blockgains.domain.model.TransactionType.SWAP
import com.tiffles.blockgains.domain.model.TransactionType.TRANSFER

/**
 * Per-type content rules for a record (import-format-spec → Unified format). Shared by the
 * importers (preview issues) and the calculator (rows it must skip), so both agree.
 */
object TransactionValidator {

    private val FEE_TYPES: Set<TransactionType> = setOf(BUY, SELL, SWAP)
    private val VALUE_TYPES: Set<TransactionType> = setOf(BUY, SELL, INCOME, OPENING)

    /** The first problem with [tx], or null if the calculator can use it. */
    fun validate(tx: Transaction): InvalidReason? =
        commonProblem(tx) ?: valueProblem(tx) ?: feeProblem(tx) ?: typeSpecificProblem(tx)

    private fun commonProblem(tx: Transaction): InvalidReason? = when {
        tx.asset.isBlank() -> InvalidReason.BLANK_ASSET
        tx.wallet.isBlank() -> InvalidReason.BLANK_WALLET
        tx.type == ADJUST && tx.quantity.signum() >= 0 -> InvalidReason.ADJUST_NOT_NEGATIVE
        tx.type != ADJUST && tx.quantity.signum() <= 0 -> InvalidReason.QUANTITY_NOT_POSITIVE
        else -> null
    }

    private fun valueProblem(tx: Transaction): InvalidReason? {
        if (tx.type !in VALUE_TYPES) return null
        val price = tx.priceUsd
        val total = tx.totalUsd
        return when {
            price == null && total == null -> InvalidReason.MISSING_VALUE
            (price?.signum() ?: 0) < 0 || (total?.signum() ?: 0) < 0 -> InvalidReason.NEGATIVE_VALUE
            price != null && total != null &&
                (price.multiply(tx.quantity) - total).abs() > TaxRules.PRICE_TOTAL_TOLERANCE ->
                InvalidReason.PRICE_TOTAL_MISMATCH
            else -> null
        }
    }

    private fun feeProblem(tx: Transaction): InvalidReason? {
        val fee = tx.feeUsd
        return when {
            fee != null && fee.signum() < 0 -> InvalidReason.NEGATIVE_FEE
            tx.type == TRANSFER -> networkFeeProblem(tx)
            tx.feeQuantity != null || !tx.feeAsset.isNullOrBlank() -> InvalidReason.NETWORK_FEE_NOT_ALLOWED
            fee != null && fee.signum() > 0 && tx.type !in FEE_TYPES -> InvalidReason.FEE_NOT_ALLOWED
            else -> null
        }
    }

    /**
     * R13: a TRANSFER's fee is paid in coins: `fee_quantity` (positive) worth `fee_usd`. A positive
     * `fee_usd` or a `fee_asset` without `fee_quantity` is rejected, never ignored. A $0 `fee_usd`
     * alone stays valid (no fee), as before R13.
     */
    private fun networkFeeProblem(tx: Transaction): InvalidReason? {
        val quantity = tx.feeQuantity
        return when {
            quantity == null ->
                if ((tx.feeUsd?.signum() ?: 0) > 0 || !tx.feeAsset.isNullOrBlank()) InvalidReason.NETWORK_FEE_MISSING_QUANTITY else null
            quantity.signum() <= 0 -> InvalidReason.NETWORK_FEE_QUANTITY_NOT_POSITIVE
            tx.feeUsd == null -> InvalidReason.NETWORK_FEE_MISSING_VALUE
            else -> null
        }
    }

    private fun typeSpecificProblem(tx: Transaction): InvalidReason? = when (tx.type) {
        SWAP -> swapProblem(tx)
        TRANSFER -> when {
            tx.toWallet.isNullOrBlank() -> InvalidReason.TRANSFER_MISSING_TO_WALLET
            tx.toWallet == tx.wallet -> InvalidReason.TRANSFER_SAME_WALLET
            else -> null
        }
        OPENING -> when {
            tx.date != TaxRules.TRANSITION_DATE -> InvalidReason.OPENING_WRONG_DATE
            tx.acquiredDate == null -> InvalidReason.OPENING_MISSING_ACQUIRED_DATE
            tx.acquiredDate.isAfter(TaxRules.TRANSITION_DATE) -> InvalidReason.OPENING_ACQUIRED_AFTER_TRANSITION
            else -> null
        }
        BUY, SELL, INCOME, ADJUST -> null
    }

    /** R6: a swap needs what was received and its USD value V. */
    private fun swapProblem(tx: Transaction): InvalidReason? = when {
        tx.toAsset.isNullOrBlank() -> InvalidReason.SWAP_MISSING_TO_ASSET
        tx.toAsset == tx.asset -> InvalidReason.SWAP_SAME_ASSET
        tx.toQuantity == null || tx.toQuantity.signum() <= 0 -> InvalidReason.SWAP_MISSING_TO_QUANTITY
        tx.totalUsd == null -> InvalidReason.MISSING_VALUE
        tx.totalUsd.signum() < 0 -> InvalidReason.NEGATIVE_VALUE
        else -> null
    }
}
