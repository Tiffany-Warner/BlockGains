package com.tiffles.blockgains.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvReaderTest {

    private fun fields(text: String) = CsvReader.read(text).map { it.fields }

    @Test
    fun quotedMoneyWithComma_staysOneField() { // v1 bug 4
        assertThat(fields("a,\"\$1,234.56\",c")).containsExactly(listOf("a", "\$1,234.56", "c"))
    }

    @Test
    fun escapedQuotesAndEmbeddedNewlines() {
        val records = CsvReader.read("x,\"say \"\"hi\"\"\nthere\",z\nnext,1,2")
        assertThat(records.map { it.fields }).containsExactly(
            listOf("x", "say \"hi\"\nthere", "z"),
            listOf("next", "1", "2"),
        ).inOrder()
        assertThat(records.map { it.lineNumber }).containsExactly(1, 3).inOrder()
    }

    @Test
    fun lineEndings_bom_andBlankLines() {
        val bom = Char(0xFEFF)
        val records = CsvReader.read("${bom}h1,h2\r\n\r\na,b\rc,d\n\n")
        assertThat(records.map { it.fields }).containsExactly(listOf("h1", "h2"), listOf("a", "b"), listOf("c", "d")).inOrder()
        assertThat(records.map { it.lineNumber }).containsExactly(1, 3, 4).inOrder()
    }

    @Test
    fun emptyAndTrailingFields() {
        assertThat(fields("a,,c,")).containsExactly(listOf("a", "", "c", ""))
        assertThat(fields("")).isEmpty()
    }

    @Test
    fun unterminatedQuote_isLenient() {
        assertThat(fields("a,\"open")).containsExactly(listOf("a", "open"))
    }
}
