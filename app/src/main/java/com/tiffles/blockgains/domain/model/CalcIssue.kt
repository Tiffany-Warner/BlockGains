package com.tiffles.blockgains.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A data problem found while importing or calculating (R10). Issues never stop the
 * calculation; each names the row/date/asset/wallet and the quantity involved.
 */
sealed interface CalcIssue {
    val date: LocalDate?
    val asset: String?

    /** A disposal, transfer or adjustment needed more units than the pool held on that date (R3). */
    data class InsufficientLots(
        val transactionId: Long,
        val type: TransactionType,
        override val date: LocalDate,
        override val asset: String,
        /** The record's wallet (a transfer's source wallet). */
        val wallet: String,
        /** True before 2025: matched against the universal pool, so [available] counts every wallet (R3). */
        val pooled: Boolean,
        val requested: BigDecimal,
        val available: BigDecimal,
        /** R13: the shortfall is in the coins paying a transfer's network fee, not the moved units. */
        val networkFee: Boolean = false,
    ) : CalcIssue {
        val shortBy: BigDecimal get() = requested - available
    }

    /** OPENING records for an asset don't add up to the universal remainder on 2025-01-01 (R4). */
    data class TransitionMismatch(
        override val asset: String,
        override val date: LocalDate,
        val expected: BigDecimal,
        val opening: BigDecimal,
    ) : CalcIssue

    /** A BUY at $0 that isn't marked unknown-basis; it might be untracked income (R9). */
    data class ZeroPriceBuy(
        val transactionId: Long,
        override val date: LocalDate,
        override val asset: String,
        val wallet: String,
        val quantity: BigDecimal,
    ) : CalcIssue

    /** A row that can't be used. [transactionId] is set for stored rows, [lineNumber] for file rows. */
    data class InvalidRow(
        val reason: InvalidReason,
        val transactionId: Long? = null,
        val lineNumber: Int? = null,
        /** File the row came from, for import issues. */
        val fileName: String? = null,
        override val date: LocalDate? = null,
        override val asset: String? = null,
        /** The offending value or column, for the message. Never a money amount. */
        val detail: String? = null,
    ) : CalcIssue
}

/** Why a row is invalid. The UI maps each to a plain-language message and fix. */
enum class InvalidReason {
    // Parsing (importers)
    MISSING_COLUMN,
    MISSING_FIELD,
    UNKNOWN_TYPE,
    UNKNOWN_ASSET_CLASS,
    BAD_DATE,
    BAD_NUMBER,
    BAD_BOOLEAN,

    // Content (TransactionValidator)
    BLANK_ASSET,
    BLANK_WALLET,
    QUANTITY_NOT_POSITIVE,
    ADJUST_NOT_NEGATIVE,
    MISSING_VALUE,
    NEGATIVE_VALUE,
    PRICE_TOTAL_MISMATCH,
    NEGATIVE_FEE,
    FEE_NOT_ALLOWED,
    NETWORK_FEE_NOT_ALLOWED,
    NETWORK_FEE_MISSING_QUANTITY,
    NETWORK_FEE_QUANTITY_NOT_POSITIVE,
    NETWORK_FEE_MISSING_VALUE,
    SWAP_MISSING_TO_ASSET,
    SWAP_MISSING_TO_QUANTITY,
    SWAP_SAME_ASSET,
    TRANSFER_MISSING_TO_WALLET,
    TRANSFER_SAME_WALLET,
    OPENING_WRONG_DATE,
    OPENING_MISSING_ACQUIRED_DATE,
    OPENING_ACQUIRED_AFTER_TRANSITION,
}
