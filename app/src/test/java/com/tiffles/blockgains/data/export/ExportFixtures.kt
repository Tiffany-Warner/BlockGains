package com.tiffles.blockgains.data.export

import com.tiffles.blockgains.domain.calc.TxBuilder
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Made-up records covering every type and the awkward cases (quotes, long decimals, rounding). */
object ExportFixtures {
    val transactions: List<Transaction> = TxBuilder().apply {
        buy("2023-01-10", qty = "0.333333333", price = "1000.005", fee = "0.015", asset = "FAKEA")
        buy("2023-02-10", qty = "0.333333333", price = "1000.005", asset = "FAKEA")
        buy("2024-03-01", qty = "0.333333334", price = "1200", asset = "FAKEA")
        sell("2024-02-15", qty = "0.5", price = "2000.003", fee = "1.005", asset = "FAKEA") // mixed LT + ST
        sell("2024-06-01", qty = "0.3", price = "1100", asset = "FAKEA")
        buy("2024-01-05", qty = "10", price = "50", asset = "FAKEE", assetClass = AssetClass.STOCK)
        sell("2024-07-05", qty = "10", price = "45.555", asset = "FAKEE", assetClass = AssetClass.STOCK)
        income("2024-04-01", qty = "0.0421", price = "3020.333", asset = "FAKEB", wallet = "FAKE-EXCHANGE-1")
        raw(
            Transaction(
                type = TransactionType.SWAP, date = LocalDate.parse("2024-09-03"), asset = "FAKEB", wallet = "FAKE-EXCHANGE-1",
                quantity = BigDecimal("0.04"), totalUsd = BigDecimal("98.765"), toAsset = "FAKEUSD", toQuantity = BigDecimal("98.765"),
                feeUsd = BigDecimal("0.5"), note = "swap, with \"quotes\"\nand a line break",
            ),
        )
        transfer("2025-02-01", qty = "50", from = "FAKE-EXCHANGE-1", to = "FAKE-WALLET-2", asset = "FAKEUSD")
        opening("FAKEA", "FAKE-WALLET-1", qty = "0.199999999", acquired = "2023-02-10", basis = "200.00", basisUnknown = true)
        adjust("2025-03-01", qty = "-0.000000001", asset = "FAKEA")
        transfer("2025-04-01", qty = "0.05", from = "FAKE-WALLET-1", to = "FAKE-WALLET-2", asset = "FAKEA", feeQty = "0.0001", feeUsd = "3.00")
    }.transactions
}
