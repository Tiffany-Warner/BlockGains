package com.tiffles.blockgains.domain.calc

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Test DSL for made-up transactions. Ids auto-increment from 1 in creation order. */
class TxBuilder(private val defaultWallet: String = "FAKE-WALLET-1") {
    private var nextId = 1L
    val transactions = mutableListOf<Transaction>()

    private fun add(tx: Transaction): Transaction = tx.copy(id = nextId++).also { transactions += it }

    fun buy(
        date: String,
        qty: String,
        price: String,
        fee: String? = null,
        asset: String = "FAKEA",
        wallet: String = defaultWallet,
        basisUnknown: Boolean = false,
        assetClass: AssetClass = AssetClass.CRYPTO,
    ) = add(
        Transaction(
            type = TransactionType.BUY, date = d(date), asset = asset, assetClass = assetClass, wallet = wallet,
            quantity = BigDecimal(qty), priceUsd = BigDecimal(price), feeUsd = fee?.let(::BigDecimal),
            basisUnknown = basisUnknown,
        ),
    )

    fun sell(
        date: String,
        qty: String,
        price: String,
        fee: String? = null,
        asset: String = "FAKEA",
        wallet: String = defaultWallet,
        assetClass: AssetClass = AssetClass.CRYPTO,
    ) = add(
        Transaction(
            type = TransactionType.SELL, date = d(date), asset = asset, assetClass = assetClass, wallet = wallet,
            quantity = BigDecimal(qty), priceUsd = BigDecimal(price), feeUsd = fee?.let(::BigDecimal),
        ),
    )

    fun opening(asset: String, wallet: String, qty: String, acquired: String, basis: String, basisUnknown: Boolean = false) =
        add(
            Transaction(
                type = TransactionType.OPENING, date = TaxRules.TRANSITION_DATE, asset = asset, wallet = wallet,
                quantity = BigDecimal(qty), totalUsd = BigDecimal(basis), acquiredDate = d(acquired),
                basisUnknown = basisUnknown,
            ),
        )

    /** [qty] is what arrived; [feeQty] units of [feeAsset] (null = [asset]) worth [feeUsd] paid the network fee (R13). */
    fun transfer(
        date: String,
        qty: String,
        from: String,
        to: String,
        asset: String = "FAKEA",
        feeQty: String? = null,
        feeUsd: String? = null,
        feeAsset: String? = null,
    ) = add(
        Transaction(
            type = TransactionType.TRANSFER, date = d(date), asset = asset, wallet = from,
            quantity = BigDecimal(qty), toWallet = to, feeQuantity = feeQty?.let(::BigDecimal),
            feeUsd = feeUsd?.let(::BigDecimal), feeAsset = feeAsset,
        ),
    )

    fun swap(date: String, qty: String, asset: String, toAsset: String, toQty: String, value: String, wallet: String, fee: String? = null) =
        add(
            Transaction(
                type = TransactionType.SWAP, date = d(date), asset = asset, wallet = wallet, quantity = BigDecimal(qty),
                totalUsd = BigDecimal(value), toAsset = toAsset, toQuantity = BigDecimal(toQty),
                feeUsd = fee?.let(::BigDecimal),
            ),
        )

    fun income(date: String, qty: String, price: String, asset: String = "FAKEB", wallet: String = defaultWallet) = add(
        Transaction(
            type = TransactionType.INCOME, date = d(date), asset = asset, wallet = wallet,
            quantity = BigDecimal(qty), priceUsd = BigDecimal(price),
        ),
    )

    fun adjust(date: String, qty: String, asset: String = "FAKEA", wallet: String = defaultWallet) = add(
        Transaction(type = TransactionType.ADJUST, date = d(date), asset = asset, wallet = wallet, quantity = BigDecimal(qty)),
    )

    fun raw(tx: Transaction) = add(tx)

    fun calculate(defaultWallet: String = this.defaultWallet): CalcResult =
        TaxCalculator(FifoStrategy).calculate(transactions, CalcSettings(defaultWallet))
}

fun d(iso: String): LocalDate = LocalDate.parse(iso)

/** Asserts [actual] equals [expected] after rounding to cents (HALF_UP), the display precision. */
fun assertCents(actual: BigDecimal, expected: String) {
    assertThat(actual.setScale(2, RoundingMode.HALF_UP)).isEqualTo(BigDecimal(expected).setScale(2, RoundingMode.HALF_UP))
}

/** Asserts exact numeric equality regardless of scale. */
fun assertExact(actual: BigDecimal, expected: String) {
    assertThat(actual).isEquivalentAccordingToCompareTo(BigDecimal(expected))
}
