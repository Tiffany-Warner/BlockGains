package com.tiffles.blockgains.ui.format

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import org.junit.Test

/** ui-guidelines → Numbers and tax-rules → Rounding (HALF_UP to cents, display only). */
class FormattersTest {

    private val minus = Char(0x2212)

    @Test
    fun money_usesGroupingCentsAndATrueMinusSign() {
        assertThat(MoneyFormatter.format(BigDecimal("1234.5"))).isEqualTo("$1,234.50")
        assertThat(MoneyFormatter.format(BigDecimal("-1234.56"))).isEqualTo("$minus$1,234.56")
        assertThat(MoneyFormatter.format(BigDecimal("1234567.891"))).isEqualTo("$1,234,567.89")
    }

    @Test
    fun money_roundsHalfUp_notHalfEven() {
        assertThat(MoneyFormatter.format(BigDecimal("0.125"))).isEqualTo("$0.13")
        assertThat(MoneyFormatter.format(BigDecimal("2.345"))).isEqualTo("$2.35")
        assertThat(MoneyFormatter.format(BigDecimal("-0.004"))).isEqualTo("$0.00") // no "−$0.00"
    }

    @Test
    fun signed_showsAPlusForGainsOnly() {
        assertThat(MoneyFormatter.signed(BigDecimal("10"))).isEqualTo("+$10.00")
        assertThat(MoneyFormatter.signed(BigDecimal("-10"))).isEqualTo("$minus$10.00")
        assertThat(MoneyFormatter.signed(BigDecimal("0.001"))).isEqualTo("$0.00")
    }

    @Test
    fun quantity_trimsToAtMostEightDecimals_fullKeepsEverything() {
        assertThat(QuantityFormatter.format(BigDecimal("1.500000000"))).isEqualTo("1.5")
        assertThat(QuantityFormatter.format(BigDecimal("0.123456789"))).isEqualTo("0.12345679")
        assertThat(QuantityFormatter.format(BigDecimal("12345.6"))).isEqualTo("12,345.6")
        assertThat(QuantityFormatter.full(BigDecimal("0.123456789012"))).isEqualTo("0.123456789012")
        assertThat(QuantityFormatter.format(BigDecimal("-0.0012"))).isEqualTo("${minus}0.0012")
    }

    @Test
    fun pluralCount_isOneOnlyForExactlyOne() {
        assertThat(QuantityFormatter.pluralCount(BigDecimal("1"))).isEqualTo(1)
        assertThat(QuantityFormatter.pluralCount(BigDecimal("1.000"))).isEqualTo(1)
        assertThat(QuantityFormatter.pluralCount(BigDecimal("0.5"))).isNotEqualTo(1)
        assertThat(QuantityFormatter.pluralCount(BigDecimal("1.5"))).isNotEqualTo(1)
        assertThat(QuantityFormatter.pluralCount(BigDecimal("3"))).isNotEqualTo(1)
    }
}
