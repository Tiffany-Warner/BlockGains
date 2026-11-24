package com.tiffles.blockgains.data.export

/** RFC 4180 writer: quotes fields containing commas, quotes or line breaks; CRLF line endings. */
object CsvWriter {
    private const val LINE_END = "\r\n"

    fun write(rows: List<List<String>>): String = buildString {
        rows.forEach { row -> append(row.joinToString(",") { escape(it) }).append(LINE_END) }
    }

    /** Adds raw lines (e.g. `#` comments) after the rows, unescaped. */
    fun write(rows: List<List<String>>, trailingLines: List<String>): String =
        write(rows) + trailingLines.joinToString("") { it + LINE_END }

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
