package com.tiffles.blockgains.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/** Asset class. Exists from day one so stocks (Phase 3) slot in; it's a filter, not a pool boundary. */
enum class AssetClass { CRYPTO, STOCK }

/** The seven record types of the unified CSV (import-format-spec → Unified format). */
enum class TransactionType { BUY, SELL, SWAP, INCOME, TRANSFER, OPENING, ADJUST }

/**
 * One raw record, mirroring a unified-CSV row: a type plus nullable type-specific columns.
 * Lots and taxable events are always derived from these, never stored.
 *
 * Money is USD; quantities keep full precision. Which fields are required per type is
 * enforced by `TransactionValidator`, not by the type system, so bad rows can still be
 * stored, shown, and reported as issues (R10).
 */
data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val date: LocalDate,
    val asset: String,
    val assetClass: AssetClass = AssetClass.CRYPTO,
    val wallet: String,
    val quantity: BigDecimal,
    val priceUsd: BigDecimal? = null,
    val totalUsd: BigDecimal? = null,
    val feeUsd: BigDecimal? = null,
    /** R13: asset a TRANSFER's network fee was paid in; null = the transferred asset. */
    val feeAsset: String? = null,
    /** R13: units spent on a TRANSFER's network fee (on top of [quantity], which is what arrived). */
    val feeQuantity: BigDecimal? = null,
    val toAsset: String? = null,
    val toQuantity: BigDecimal? = null,
    val toWallet: String? = null,
    val acquiredDate: LocalDate? = null,
    val basisUnknown: Boolean = false,
    val note: String? = null,
) {
    /** USD value of the row: `total_usd` if given, else `price_usd × |quantity|`. */
    val usdValue: BigDecimal?
        get() = totalUsd ?: priceUsd?.multiply(quantity.abs())

    /** Fee in USD, 0 when absent (R7). */
    val fee: BigDecimal
        get() = feeUsd ?: BigDecimal.ZERO

    /** R13: the asset a network fee was paid in, or null when the row has no fee in coins. */
    val networkFeeAsset: String?
        get() = feeQuantity?.let { feeAsset ?: asset }
}
