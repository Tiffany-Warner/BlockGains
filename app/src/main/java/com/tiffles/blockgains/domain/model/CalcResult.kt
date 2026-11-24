package com.tiffles.blockgains.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/** R2 holding period of one matched lot. */
enum class HoldingTerm { SHORT, LONG }

/** Which R3 pool a disposal matched against. */
enum class MatchingScope {
    /** Before 2025-01-01: FIFO per asset across all wallets. */
    UNIVERSAL,

    /** On/after 2025-01-01: FIFO per (asset, wallet). */
    PER_WALLET,
}

/** A lot still held: what's left of an acquisition (BUY, INCOME, swap-in, OPENING). */
data class Lot(
    val sourceTransactionId: Long,
    val asset: String,
    val assetClass: AssetClass,
    val wallet: String,
    val acquiredDate: LocalDate,
    val quantity: BigDecimal,
    /** Total remaining basis of this lot (not per unit), fees included (R7). */
    val basis: BigDecimal,
    val basisUnknown: Boolean,
)

/** The part of one lot consumed by a disposal. */
data class LotMatch(
    val sourceTransactionId: Long,
    /** Wallet the lot sat in when matched (differs from the event's wallet only in the universal pool). */
    val wallet: String,
    /** Null for units a disposal used without a matching lot (shown as "Unknown"; tax-rules R3). */
    val acquiredDate: LocalDate?,
    val quantity: BigDecimal,
    val basis: BigDecimal,
    /** Amount realized allocated to this lot, after its share of the fee. */
    val proceeds: BigDecimal,
    /** This lot's pro-rata share of the disposal fee (R7), already subtracted from [proceeds]. */
    val fee: BigDecimal,
    val term: HoldingTerm,
    val basisUnknown: Boolean,
) {
    val gain: BigDecimal get() = proceeds - basis
}

/** A matched disposal (SELL, the outgoing side of a SWAP, or a TRANSFER's network fee, R13). R1. */
data class TaxableEvent(
    val transactionId: Long,
    val type: TransactionType,
    val date: LocalDate,
    val asset: String,
    val assetClass: AssetClass,
    val wallet: String,
    val scope: MatchingScope,
    val lots: List<LotMatch>,
    /** For a SWAP: the asset received. */
    val receivedAsset: String? = null,
) {
    val quantity: BigDecimal get() = lots.sumOf { it.quantity }
    val proceeds: BigDecimal get() = lots.sumOf { it.proceeds }
    val basis: BigDecimal get() = lots.sumOf { it.basis }
    val fee: BigDecimal get() = lots.sumOf { it.fee }
    val gain: BigDecimal get() = proceeds - basis
    val shortTermGain: BigDecimal get() = lots.filter { it.term == HoldingTerm.SHORT }.sumOf { it.gain }
    val longTermGain: BigDecimal get() = lots.filter { it.term == HoldingTerm.LONG }.sumOf { it.gain }

    /** R13: the coins spent on a transfer's network fee ([asset] is the fee's asset). */
    val isTransferFee: Boolean get() = type == TransactionType.TRANSFER

    /** Single term if every lot shares it, null when the disposal mixes short- and long-term lots. */
    val term: HoldingTerm? get() = lots.map { it.term }.distinct().singleOrNull()
}

/** Ordinary income from an INCOME record (R8). */
data class IncomeEvent(
    val transactionId: Long,
    val date: LocalDate,
    val asset: String,
    val assetClass: AssetClass,
    val wallet: String,
    val quantity: BigDecimal,
    val valueUsd: BigDecimal,
    val note: String?,
)

/** Units removed by a negative ADJUST, with no gain or loss (R9). */
data class Adjustment(
    val transactionId: Long,
    val date: LocalDate,
    val asset: String,
    val wallet: String,
    val quantity: BigDecimal,
    val basisRemoved: BigDecimal,
)

/** Everything the calculator derives from the raw records. Pure data, recomputed on every change. */
data class CalcResult(
    val taxableEvents: List<TaxableEvent>,
    val incomeEvents: List<IncomeEvent>,
    val adjustments: List<Adjustment>,
    val remainingLots: List<Lot>,
    /** What the universal (pre-2025) pool held on the transition date, before OPENING records applied (R4). */
    val universalLotsAtTransition: List<Lot>,
    val issues: List<CalcIssue>,
) {
    companion object {
        val EMPTY = CalcResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Calculator inputs that aren't records. */
data class CalcSettings(
    /** Receives the universal remainder of assets without OPENING records (R4). */
    val defaultWallet: String,
)
