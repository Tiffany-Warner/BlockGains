package com.tiffles.blockgains.data.importer

import com.tiffles.blockgains.domain.model.Transaction

/**
 * The unified CSV (import-format-spec → Unified format). The importer, the "All transactions"
 * export and the blank template all use this one definition, so export → import round-trips.
 */
object UnifiedCsvFormat {
    const val TYPE = "type"
    const val DATE = "date"
    const val ASSET = "asset"
    const val ASSET_CLASS = "asset_class"
    const val WALLET = "wallet"
    const val QUANTITY = "quantity"
    const val PRICE_USD = "price_usd"
    const val TOTAL_USD = "total_usd"
    const val FEE_USD = "fee_usd"
    const val FEE_ASSET = "fee_asset"
    const val FEE_QUANTITY = "fee_quantity"
    const val TO_ASSET = "to_asset"
    const val TO_QUANTITY = "to_quantity"
    const val TO_WALLET = "to_wallet"
    const val ACQUIRED_DATE = "acquired_date"
    const val BASIS_UNKNOWN = "basis_unknown"
    const val NOTE = "note"

    val HEADER: List<String> = listOf(
        TYPE, DATE, ASSET, ASSET_CLASS, WALLET, QUANTITY, PRICE_USD, TOTAL_USD, FEE_USD, FEE_ASSET, FEE_QUANTITY,
        TO_ASSET, TO_QUANTITY, TO_WALLET, ACQUIRED_DATE, BASIS_UNKNOWN, NOTE,
    )

    val REQUIRED: List<String> = listOf(TYPE, DATE, ASSET, WALLET, QUANTITY)

    /** Lines whose first field starts with this are comments (the template's example rows). */
    const val COMMENT_PREFIX = "#"

    /** Fields in [HEADER] order. Numbers as plain strings at full precision; dates ISO. */
    fun fields(tx: Transaction): List<String> = listOf(
        tx.type.name,
        tx.date.toString(),
        tx.asset,
        tx.assetClass.name,
        tx.wallet,
        tx.quantity.toPlainString(),
        tx.priceUsd?.toPlainString().orEmpty(),
        tx.totalUsd?.toPlainString().orEmpty(),
        tx.feeUsd?.toPlainString().orEmpty(),
        tx.feeAsset.orEmpty(),
        tx.feeQuantity?.toPlainString().orEmpty(),
        tx.toAsset.orEmpty(),
        tx.toQuantity?.toPlainString().orEmpty(),
        tx.toWallet.orEmpty(),
        tx.acquiredDate?.toString().orEmpty(),
        if (tx.basisUnknown) "true" else "",
        tx.note.orEmpty(),
    )

    /** Blank template: header plus commented, made-up example rows for every type. */
    val TEMPLATE_EXAMPLES: List<String> = listOf(
        "# Example rows (made-up numbers). Lines starting with # are ignored. Delete these and add your own.",
        "# price_usd and total_usd are BEFORE fees: the subtotal before fees, not the total including fees.",
        "# Put fees only in fee_usd: a buy fee is added to basis; a sell/swap fee is subtracted from proceeds (R7).",
        "# TRANSFER: quantity = amount received; network fee coins go in fee_quantity/fee_asset; their USD value in fee_usd (R13).",
        "# BUY,2023-01-05,FAKEB,CRYPTO,FAKE-WALLET-2,10,20.00,,,,,,,,,,",
        "# OPENING,2025-01-01,FAKEB,CRYPTO,FAKE-WALLET-2,10,20.00,,,,,,,,2023-01-05,,carried from universal pool",
        "# SWAP,2025-03-10,FAKEB,CRYPTO,FAKE-WALLET-2,9.9,,1500.00,,,,FAKEUSD,1500,,,,on-chain swap",
        "# TRANSFER,2025-03-12,FAKEUSD,CRYPTO,FAKE-WALLET-2,1500,,,,,,,,FAKE-EXCHANGE-1,,,",
        "# TRANSFER,2025-03-13,FAKEB,CRYPTO,FAKE-WALLET-2,0.09,,,3.00,,0.01,,,FAKE-EXCHANGE-1,,,quantity = received; network fee 0.01 FAKEB worth $3.00",
        "# SELL,2025-03-15,FAKEUSD,CRYPTO,FAKE-EXCHANGE-1,1500,1.00,,,,,,,,,,",
        "# INCOME,2025-02-01,FAKEB,CRYPTO,FAKE-EXCHANGE-1,0.5,100.00,,,,,,,,,,staking",
        "# ADJUST,2025-06-01,FAKEB,CRYPTO,FAKE-WALLET-1,-0.0012,,,,,,,,,,,fee dust",
    )
}
