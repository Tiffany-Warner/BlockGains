package com.tiffles.blockgains.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The only place money is formatted for display (ui-guidelines → Numbers): `$1,234.56`,
 * `−$1,234.56` with a true minus sign. Values are rounded HALF_UP to cents here and only
 * here (tax-rules → Rounding).
 */
object MoneyFormatter {
    private val MINUS: Char = Char(0x2212)

    fun format(value: BigDecimal): String {
        val cents = value.setScale(2, RoundingMode.HALF_UP)
        val text = "$" + pattern("#,##0.00").format(cents.abs())
        return if (cents.signum() < 0) "$MINUS$text" else text
    }

    /** Gain/loss: explicit `+` for gains so the sign, not just color, carries meaning. */
    fun signed(value: BigDecimal): String {
        val cents = value.setScale(2, RoundingMode.HALF_UP)
        return if (cents.signum() > 0) "+" + format(cents) else format(cents)
    }

    /** DecimalFormat isn't thread-safe; a new one per call is cheap and safe from any dispatcher. */
    private fun pattern(pattern: String) = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
}

/** Record counts: `1,234`. */
object CountFormatter {
    fun format(count: Int): String = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(count)
}

/** Quantities: trimmed to significant decimals, at most 8 shown; [full] keeps every digit. */
object QuantityFormatter {
    private const val MAX_SHOWN_DECIMALS = 8

    fun format(value: BigDecimal): String {
        val shown = if (value.scale() > MAX_SHOWN_DECIMALS) value.setScale(MAX_SHOWN_DECIMALS, RoundingMode.HALF_UP) else value
        return group(shown.stripTrailingZeros())
    }

    fun full(value: BigDecimal): String = group(value.stripTrailingZeros())

    /**
     * Count for a plurals lookup with a decimal quantity: 1 only when the value is exactly one
     * ("1 unit"), otherwise a non-one count ("0.5 units", "2 units"). English: "one" means exactly 1.
     */
    fun pluralCount(value: BigDecimal): Int = if (value.compareTo(BigDecimal.ONE) == 0) 1 else 2

    private fun group(value: BigDecimal): String {
        val plain = value.abs().toPlainString()
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', missingDelimiterValue = "")
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        val sign = if (value.signum() < 0) Char(0x2212).toString() else ""
        return sign + grouped + if (fraction.isEmpty()) "" else ".$fraction"
    }
}

/** Dates in the user's locale, e.g. "Mar 1, 2025". */
object DateFormatter {
    fun format(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(date)
}
