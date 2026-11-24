package com.tiffles.blockgains.ui.preview

import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.CalcResult
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Made-up records for Compose previews: multi-lot events, a long asset name, losses,
 * transfers with network fees (R13), a swap, income and an impossible sale (so issues show up).
 */
object PreviewData {
    val transactions: List<Transaction> = listOf(
        tx(1, TransactionType.BUY, "2023-01-10", "FAKEB", "FAKE-WALLET-1", "2", price = "1500", fee = "4.25"),
        tx(2, TransactionType.BUY, "2023-06-01", "FAKEB", "FAKE-WALLET-1", "1.25", price = "1890.10"),
        tx(3, TransactionType.BUY, "2024-02-11", "FAKE-LONG-NAME-TOKEN", "FAKE-EXCHANGE-1", "1234.567891234", price = "4.31"),
        tx(4, TransactionType.SELL, "2024-03-01", "FAKEB", "FAKE-WALLET-1", "2.5", price = "3410.55", fee = "6"),
        tx(5, TransactionType.SELL, "2024-08-20", "FAKE-LONG-NAME-TOKEN", "FAKE-EXCHANGE-1", "1000", price = "3.02"),
        tx(6, TransactionType.INCOME, "2024-05-02", "FAKEB", "FAKE-EXCHANGE-1", "0.0421", price = "3020"),
        Transaction(
            id = 7, type = TransactionType.SWAP, date = LocalDate.parse("2024-09-03"), asset = "FAKEB", wallet = "FAKE-WALLET-1",
            quantity = BigDecimal("0.5"), totalUsd = BigDecimal("1180"), toAsset = "FAKEUSD", toQuantity = BigDecimal("1180"),
            note = "made-up swap",
        ),
        Transaction(
            id = 8, type = TransactionType.TRANSFER, date = LocalDate.parse("2025-02-01"), asset = "FAKEUSD", wallet = "FAKE-WALLET-1",
            quantity = BigDecimal("1180"), toWallet = "FAKE-EXCHANGE-1",
            feeUsd = BigDecimal("2.40"), feeAsset = "FAKEB", feeQuantity = BigDecimal("0.0008"),
        ),
        tx(9, TransactionType.SELL, "2024-10-10", "FAKEA", "FAKE-EXCHANGE-1", "0.1", price = "61000"),
        Transaction(
            id = 10, type = TransactionType.TRANSFER, date = LocalDate.parse("2025-03-01"), asset = "FAKEB", wallet = "FAKE-WALLET-1",
            quantity = BigDecimal("0.2"), toWallet = "FAKE-EXCHANGE-1", feeUsd = BigDecimal("5.10"), feeQuantity = BigDecimal("0.0015"),
        ),
    )

    val result: CalcResult = TaxCalculator().calculate(transactions, CalcSettings("FAKE-WALLET-1"))

    private fun tx(id: Long, type: TransactionType, date: String, asset: String, wallet: String, qty: String, price: String, fee: String? = null) =
        Transaction(
            id = id, type = type, date = LocalDate.parse(date), asset = asset, assetClass = AssetClass.CRYPTO, wallet = wallet,
            quantity = BigDecimal(qty), priceUsd = BigDecimal(price), feeUsd = fee?.let(::BigDecimal),
        )
}
