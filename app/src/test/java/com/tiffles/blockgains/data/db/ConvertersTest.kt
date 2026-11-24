package com.tiffles.blockgains.data.db

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun decimals_roundTripWithFullPrecisionAndScale() {
        listOf("0.000000001", "1234.50", "-0.0012", "100000000000.123456789012").forEach {
            val stored = converters.fromDecimal(BigDecimal(it))
            assertThat(stored).isEqualTo(it)
            assertThat(converters.toDecimal(stored)).isEqualTo(BigDecimal(it))
        }
        assertThat(converters.fromDecimal(BigDecimal("1E+3"))).isEqualTo("1000") // never scientific
    }

    @Test
    fun dates_roundTripAsIso() {
        val date = LocalDate.of(2024, 2, 29)
        assertThat(converters.fromDate(date)).isEqualTo("2024-02-29")
        assertThat(converters.toDate("2024-02-29")).isEqualTo(date)
        assertThat(converters.toDate(null)).isNull()
    }

    @Test
    fun entityMapping_roundTrips() {
        val tx = Transaction(
            id = 7, type = TransactionType.SWAP, date = LocalDate.of(2025, 3, 10), asset = "FAKEB",
            assetClass = AssetClass.CRYPTO, wallet = "FAKE-WALLET-2", quantity = BigDecimal("10"),
            totalUsd = BigDecimal("1500.00"), feeUsd = BigDecimal("1"), toAsset = "FAKEUSD", toQuantity = BigDecimal("1500"),
            note = "made up",
        )
        assertThat(tx.toEntity().toDomain()).isEqualTo(tx)
    }
}
