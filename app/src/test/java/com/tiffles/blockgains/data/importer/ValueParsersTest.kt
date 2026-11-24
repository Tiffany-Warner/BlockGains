package com.tiffles.blockgains.data.importer

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test

class ValueParsersTest {

    @Test
    fun dates_inEveryAcceptedFormat() {
        val expected = LocalDate.of(2021, 9, 4)
        listOf("9/4/2021", "09/04/2021", "2021-09-04", "2021/09/04", " 2021-9-4 ").forEach {
            assertThat(ValueParsers.date(it)).isEqualTo(expected)
        }
    }

    @Test
    fun dates_rejectImpossibleAndGarbage() {
        listOf("2/30/2023", "2023-13-01", "13/01/2023", "yesterday", "").forEach {
            assertThat(ValueParsers.date(it)).isNull()
        }
    }

    @Test
    fun decimals_parseMoneyFormats() {
        mapOf(
            "\$1,234.56" to "1234.56",
            " 12 " to "12",
            "\$0.00" to "0.00",
            "-\$5.10" to "-5.10",
            "\$-5.10" to "-5.10",
            "${Char(0x2212)}5" to "-5",
            ".5" to "0.5",
            "1,500" to "1500",
        ).forEach { (raw, expected) ->
            assertThat(ValueParsers.decimal(raw)).isEquivalentAccordingToCompareTo(BigDecimal(expected))
        }
    }

    @Test
    fun decimals_keepFullPrecision() {
        assertThat(ValueParsers.decimal("0.123456789012345678")!!.toPlainString()).isEqualTo("0.123456789012345678")
    }

    @Test
    fun decimals_rejectAmbiguousOrGarbage() {
        listOf("1,5", "12,34.5", "abc", "1.2.3", "$", "", "1,5e3", "1,234E2", "1e", "e5", "1e5.5", "1e1234", "1E--3").forEach {
            assertThat(ValueParsers.decimal(it)).isNull()
        }
    }

    @Test
    fun decimals_acceptScientificNotation_exactly() {
        mapOf(
            "0E-8" to "0",
            "1.2E-05" to "0.000012",
            "1.5e3" to "1500",
            "2.5E+2" to "250",
            "-1.2E-3" to "-0.0012",
            "\$1.5E3" to "1500",
            "1.23456789012345678E-12" to "0.00000000000123456789012345678",
        ).forEach { (raw, expected) ->
            val parsed = ValueParsers.decimal(raw)
            assertThat(parsed).isEquivalentAccordingToCompareTo(BigDecimal(expected))
            assertThat(ValueParsers.isScientific(raw)).isTrue()
        }
        listOf("1500", "\$1,500.00", "0.000012", "abc", "1,5e3").forEach { assertThat(ValueParsers.isScientific(it)).isFalse() }
    }

    @Test
    fun booleans() {
        assertThat(ValueParsers.boolean("TRUE")).isTrue()
        assertThat(ValueParsers.boolean("")).isFalse()
        assertThat(ValueParsers.boolean("maybe")).isNull()
    }
}
