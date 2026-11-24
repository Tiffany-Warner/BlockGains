package com.tiffles.blockgains.data.importer

/** One CSV record. [lineNumber] is the 1-based physical line where the record starts. */
data class CsvRecord(val lineNumber: Int, val fields: List<String>)

/**
 * RFC 4180 reader: quoted fields, commas and line breaks inside quotes, `""` escapes,
 * CRLF/LF/CR line endings, optional UTF-8 BOM. Blank lines are skipped. Never `split(",")`
 * (v1 bug 4). Lenient on malformed input: an unterminated quote runs to end of file.
 */
object CsvReader {

    /** UTF-8 BOM some spreadsheet apps prepend. */
    private val BYTE_ORDER_MARK: String = Char(0xFEFF).toString()

    fun read(text: String): List<CsvRecord> {
        val parser = Parser(text.removePrefix(BYTE_ORDER_MARK))
        return generateSequence { parser.nextRecord() }
            .filterNot { it.fields.size == 1 && it.fields[0].isBlank() }
            .toList()
    }

    private class Parser(private val text: String) {
        private var pos = 0
        private var line = 1

        fun nextRecord(): CsvRecord? {
            if (pos >= text.length) return null
            val startLine = line
            val fields = mutableListOf<String>()
            while (true) {
                fields += nextField()
                when {
                    pos >= text.length -> break
                    text[pos] == ',' -> pos++
                    else -> {
                        consumeLineBreak()
                        break
                    }
                }
            }
            return CsvRecord(startLine, fields)
        }

        private fun nextField(): String =
            if (pos < text.length && text[pos] == '"') quotedField() else plainField()

        private fun plainField(): String {
            val start = pos
            while (pos < text.length && text[pos] != ',' && text[pos] != '\n' && text[pos] != '\r') pos++
            return text.substring(start, pos)
        }

        private fun quotedField(): String {
            pos++ // opening quote
            val value = StringBuilder()
            while (pos < text.length) {
                val c = text[pos]
                when {
                    c == '"' && pos + 1 < text.length && text[pos + 1] == '"' -> {
                        value.append('"')
                        pos += 2
                    }
                    c == '"' -> {
                        pos++
                        break
                    }
                    else -> {
                        if (c == '\n' || (c == '\r' && text.getOrNull(pos + 1) != '\n')) line++
                        value.append(c)
                        pos++
                    }
                }
            }
            // Tolerate stray characters between the closing quote and the delimiter.
            value.append(plainField())
            return value.toString()
        }

        private fun consumeLineBreak() {
            if (text[pos] == '\r') pos++
            if (pos < text.length && text[pos] == '\n') pos++
            line++
        }
    }
}
