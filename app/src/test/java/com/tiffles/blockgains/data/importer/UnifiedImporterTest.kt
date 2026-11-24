package com.tiffles.blockgains.data.importer

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test

class UnifiedImporterTest {

    private val result = fixture("unified.csv").let { UnifiedImporter().parse(it.text, it.fileName) }

    @Test
    fun parsesEveryType_sortedByDate() {
        assertThat(result.rows.map { it.transaction.type }.toSet()).containsExactlyElementsIn(TransactionType.entries)
        assertThat(result.rows).hasSize(9)
        val dates = result.rows.map { it.transaction.date }
        assertThat(dates).isInOrder()
    }

    @Test
    fun headerMatchingIgnoresCaseAndSpaces_andAssetIsUppercased() {
        val buy = result.rows.first { it.lineNumber == 3 }.transaction
        assertThat(buy.type).isEqualTo(TransactionType.BUY)
        assertThat(buy.asset).isEqualTo("FAKEB")
        assertThat(buy.priceUsd).isEquivalentAccordingToCompareTo(BigDecimal("20"))
    }

    @Test
    fun typeSpecificColumns() {
        val byLine = result.rows.associateBy { it.lineNumber }
        val opening = byLine.getValue(4).transaction
        assertThat(opening.acquiredDate).isEqualTo(LocalDate.of(2023, 1, 5))
        val swap = byLine.getValue(5).transaction
        assertThat(swap.toAsset).isEqualTo("FAKEUSD")
        assertThat(swap.totalUsd).isEquivalentAccordingToCompareTo(BigDecimal("1500"))
        assertThat(byLine.getValue(6).transaction.toWallet).isEqualTo("FAKE-EXCHANGE-1")
        assertThat(byLine.getValue(7).transaction.assetClass).isEqualTo(AssetClass.CRYPTO) // blank → default
        assertThat(byLine.getValue(9).transaction.quantity).isEquivalentAccordingToCompareTo(BigDecimal("-0.0012"))
        assertThat(byLine.getValue(11).transaction.basisUnknown).isTrue()
    }

    @Test
    fun networkFeeColumns_r13() {
        val csv = """
            type,date,asset,asset_class,wallet,quantity,price_usd,total_usd,fee_usd,fee_asset,fee_quantity,to_asset,to_quantity,to_wallet,acquired_date,basis_unknown,note
            TRANSFER,2025-04-01,FAKEB,,FAKE-WALLET-1,0.99,,,30.00,,0.01,,,FAKE-EXCHANGE-1,,,
            TRANSFER,2025-04-02,FAKEUSD,,FAKE-WALLET-1,100,,,15.00,fakeb,0.005,,,FAKE-EXCHANGE-1,,,
            TRANSFER,2025-04-03,FAKEB,,FAKE-WALLET-1,1,,,5.00,,,,,FAKE-EXCHANGE-1,,,
            TRANSFER,2025-04-04,FAKEB,,FAKE-WALLET-1,1,,,,,0.01,,,FAKE-EXCHANGE-1,,,
            SELL,2025-04-05,FAKEB,,FAKE-WALLET-1,1,100,,,,0.01,,,,,,
        """.trimIndent()
        val parsed = UnifiedImporter().parse(csv, "fees.csv")

        val (same, other) = parsed.rows.map { it.transaction }
        assertThat(same.feeAsset).isNull()
        assertThat(same.networkFeeAsset).isEqualTo("FAKEB")
        assertThat(same.feeQuantity).isEquivalentAccordingToCompareTo(BigDecimal("0.01"))
        assertThat(same.feeUsd).isEquivalentAccordingToCompareTo(BigDecimal("30"))
        assertThat(other.feeAsset).isEqualTo("FAKEB") // uppercased
        assertThat(parsed.issues.map { it.reason to it.lineNumber }).containsExactly(
            InvalidReason.NETWORK_FEE_MISSING_QUANTITY to 4,
            InvalidReason.NETWORK_FEE_MISSING_VALUE to 5,
            InvalidReason.NETWORK_FEE_NOT_ALLOWED to 6,
        )
    }

    @Test
    fun quotedMoney_fees_notesAndLongDecimals() {
        val buy = result.rows.first { it.lineNumber == 10 }.transaction
        assertThat(buy.priceUsd).isEquivalentAccordingToCompareTo(BigDecimal("1234.56"))
        assertThat(buy.feeUsd).isEquivalentAccordingToCompareTo(BigDecimal("1.50"))
        assertThat(buy.quantity.toPlainString()).isEqualTo("0.012345678912")
        assertThat(buy.note).isEqualTo("note with \"quotes\", and a comma")
    }

    @Test
    fun badRows_becomeIssuesWithLineNumbers_andCommentsAreSkipped() {
        assertThat(result.issues.map { it.lineNumber to it.reason }).containsExactly(
            13 to InvalidReason.UNKNOWN_TYPE,
            14 to InvalidReason.BAD_DATE,
            15 to InvalidReason.MISSING_VALUE,
            16 to InvalidReason.BAD_NUMBER,
        )
        assertThat(result.issues.all { it.fileName == "unified.csv" }).isTrue()
    }

    @Test
    fun scientificNotation_isParsedExactly_andCountedPerRow() {
        val csv = """
            type,date,asset,wallet,quantity,price_usd,total_usd,fee_usd,acquired_date
            BUY,2024-01-01,FAKEA,FAKE-WALLET-1,1.2E-05,1.5e3,,0E-8,
            BUY,2024-01-02,FAKEA,FAKE-WALLET-1,2.5E+6,0.01,,,
            OPENING,2025-01-01,FAKEB,FAKE-WALLET-1,3,,0E-8,,2023-01-01
            ADJUST,2025-02-01,FAKEA,FAKE-WALLET-1,-1.2E-3,,,,
            SELL,2025-03-01,FAKEA,FAKE-WALLET-1,1,1.5E3,,,
        """.trimIndent()
        val parsed = UnifiedImporter().parse(csv, "sci.csv")

        assertThat(parsed.issues).isEmpty()
        val byLine = parsed.rows.associateBy { it.lineNumber }
        val tiny = byLine.getValue(2)
        assertThat(tiny.transaction.quantity).isEquivalentAccordingToCompareTo(BigDecimal("0.000012")) // tiny
        assertThat(tiny.transaction.priceUsd).isEquivalentAccordingToCompareTo(BigDecimal("1500")) // lowercase e
        assertThat(tiny.transaction.feeUsd!!.signum()).isEqualTo(0) // zero
        assertThat(tiny.scientificValues).isEqualTo(3)
        assertThat(byLine.getValue(3).transaction.quantity).isEquivalentAccordingToCompareTo(BigDecimal("2500000")) // large
        assertThat(byLine.getValue(3).scientificValues).isEqualTo(1)
        assertThat(byLine.getValue(4).transaction.totalUsd!!.signum()).isEqualTo(0)
        val adjust = byLine.getValue(5).transaction
        assertThat(adjust.type).isEqualTo(TransactionType.ADJUST)
        assertThat(adjust.quantity).isEquivalentAccordingToCompareTo(BigDecimal("-0.0012")) // negative exponent, negative value
        assertThat(parsed.rows.sumOf { it.scientificValues }).isEqualTo(7)
    }

    @Test
    fun scientificNotation_withGrouping_isStillABadNumber() {
        val parsed = UnifiedImporter().parse("type,date,asset,wallet,quantity,price_usd\nBUY,2024-01-01,FAKEA,FAKE-WALLET-1,\"1,5E3\",1", "x.csv")
        assertThat(parsed.rows).isEmpty()
        assertThat(parsed.issues.single().reason).isEqualTo(InvalidReason.BAD_NUMBER)
    }

    @Test
    fun missingRequiredColumn_isOneFileLevelIssue() {
        val parsed = UnifiedImporter().parse("type,date,asset,quantity\nBUY,2024-01-01,FAKEA,1", "x.csv")
        assertThat(parsed.rows).isEmpty()
        val issue = parsed.issues.single()
        assertThat(issue.reason).isEqualTo(InvalidReason.MISSING_COLUMN)
        assertThat(issue.detail).isEqualTo("wallet")
    }
}
