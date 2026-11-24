package com.tiffles.blockgains.data.importer

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Field parsers for every CSV format (import-format-spec → General parsing rules). */
object ValueParsers {

    // "M"/"d" accept one or two digits when parsing, so these also cover MM/dd/yyyy.
    private val DATE_FORMATS: List<DateTimeFormatter> = listOf("M/d/uuuu", "uuuu-M-d", "uuuu/M/d")
        .map { DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT) }

    /** U+2212, which the app itself uses when displaying negatives. */
    private val TRUE_MINUS: Char = Char(0x2212)

    private val PLAIN_NUMBER = Regex("""\d+\.?\d*|\.\d+""")

    /**
     * Plain number with an exponent (`0E-8`, `1.2E-05`, `1.5e3`), as spreadsheets write them.
     * Never grouped; up to 3 exponent digits, so a typo can't make a number thousands of digits long.
     */
    private val SCIENTIFIC_NUMBER = Regex("""(\d+\.?\d*|\.\d+)[eE][+-]?\d{1,3}""")
    private val GROUPED_NUMBER = Regex("""\d{1,3}(,\d{3})+(\.\d*)?""")

    /** `M/d/yyyy`, `MM/dd/yyyy`, `yyyy-MM-dd` or `yyyy/MM/dd`; null if none match or the date doesn't exist. */
    fun date(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return DATE_FORMATS.firstNotNullOfOrNull { format ->
            try {
                LocalDate.parse(text, format)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * Money or quantity straight to [BigDecimal], never via Double. Accepts an optional `$`,
     * thousands separators in proper groups (`1,234.56`; `1,5` is rejected, not read as 15),
     * scientific notation (`1.2E-05`, parsed exactly; not combined with grouping),
     * surrounding spaces and a leading minus. Null if blank or unparseable.
     */
    fun decimal(raw: String): BigDecimal? {
        val (negative, text) = unsigned(raw)
        val digits = when {
            PLAIN_NUMBER.matches(text) || SCIENTIFIC_NUMBER.matches(text) -> text
            GROUPED_NUMBER.matches(text) -> text.replace(",", "")
            else -> return null
        }
        return BigDecimal(digits).let { if (negative) it.negate() else it }
    }

    /** True if [raw] is a number written in scientific notation, which [decimal] accepts. */
    fun isScientific(raw: String): Boolean = SCIENTIFIC_NUMBER.matches(unsigned(raw).second)

    /** Strips spaces, a leading minus (ASCII or U+2212) and `$`, in either order. Returns (negative, rest). */
    private fun unsigned(raw: String): Pair<Boolean, String> {
        var text = raw.trim().replace(TRUE_MINUS, '-')
        var negative = false
        if (text.startsWith("-")) {
            negative = true
            text = text.drop(1).trim()
        }
        if (text.startsWith("$")) text = text.drop(1).trim()
        if (!negative && text.startsWith("-")) { // "$-5.00"
            negative = true
            text = text.drop(1).trim()
        }
        return negative to text
    }

    /** `true/false`, `yes/no`, `y/n`, `1/0`; blank means false. Null if unrecognized. */
    fun boolean(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "", "false", "no", "n", "0" -> false
        "true", "yes", "y", "1" -> true
        else -> null
    }
}
