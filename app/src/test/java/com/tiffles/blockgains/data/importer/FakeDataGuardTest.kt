package com.tiffles.blockgains.data.importer

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.data.export.UnifiedCsvExporter
import java.io.File
import org.junit.Test

/**
 * The repo is public: every fixture CSV and the bundled import template may only use obviously fake
 * names (ai-guidelines → Private data). Assets start with `FAKE`, wallets with `FAKE-`.
 * Commented-out data rows (the template's examples) are checked too.
 */
class FakeDataGuardTest {

    @Test
    fun fixturesUseOnlyFakeAssetsAndWallets() {
        val dir = File(checkNotNull(javaClass.getResource("/fixtures")) { "Missing fixtures dir" }.toURI())
        val csvs = dir.walkTopDown().filter { it.isFile && it.extension == "csv" }.toList() // includes differential/
        assertThat(csvs).isNotEmpty()
        csvs.forEach { assertThat(violations(it.name, it.readText())).isEmpty() }
    }

    /**
     * The converter's test data (`tools/reference_calc/testdata`, relative to the app module, where
     * Gradle runs unit tests). Legacy buys/sells files have no asset or wallet columns, so this mostly
     * guards future files there; it fails if the folder disappears so the check can't go quiet.
     */
    @Test
    fun toolTestDataUsesOnlyFakeAssetsAndWallets() {
        val dir = File("../tools/reference_calc/testdata")
        assertThat(dir.isDirectory).isTrue()
        val csvs = dir.walkTopDown().filter { it.isFile && it.extension == "csv" }.toList()
        assertThat(csvs).isNotEmpty()
        csvs.forEach { assertThat(violations(it.name, it.readText())).isEmpty() }
    }

    @Test
    fun importTemplateUsesOnlyFakeAssetsAndWallets() {
        val template = UnifiedCsvExporter().template()
        assertThat(violations("template", template)).isEmpty()
        assertThat(checkedValues(template)).isNotEmpty()
    }

    @Test
    fun guardCatchesRealLookingNames() {
        val csv = "type,date,asset,wallet,quantity,to_asset,to_wallet\nBUY,2025-01-01,ABC,FAKE-W,1,,\n# SWAP,2025-01-02,FAKEA,MYWALLET,1,XYZ,FAKE-W\n"
        assertThat(violations("sample", csv)).containsExactly(
            "sample line 2: asset ABC", "sample line 3: wallet MYWALLET", "sample line 3: to_asset XYZ",
        )
    }

    private fun violations(name: String, text: String): List<String> =
        checkedValues(text).mapNotNull { (line, column, value) ->
            val prefix = if (column in WALLET_COLUMNS) "FAKE-" else "FAKE"
            if (value.uppercase().startsWith(prefix)) null else "$name line $line: $column $value"
        }

    /** (line, column, value) for every non-blank asset/wallet cell, including `#`-commented data rows. */
    private fun checkedValues(text: String): List<Triple<Int, String, String>> {
        val records = CsvReader.read(text)
        val header = records.firstOrNull { !isComment(it) } ?: return emptyList()
        val columns = header.fields.withIndex()
            .associate { (i, name) -> name.trim().lowercase() to i }
            .filterKeys { it in ASSET_COLUMNS || it in WALLET_COLUMNS }
        return records.filter { it !== header && it.fields.size == header.fields.size }.flatMap { record ->
            val fields = record.fields.toMutableList().also { it[0] = it[0].trim().removePrefix(UnifiedCsvFormat.COMMENT_PREFIX) }
            columns.mapNotNull { (column, i) -> fields[i].trim().takeIf { it.isNotEmpty() }?.let { Triple(record.lineNumber, column, it) } }
        }
    }

    private fun isComment(record: CsvRecord) = record.fields.first().trim().startsWith(UnifiedCsvFormat.COMMENT_PREFIX)

    private companion object {
        val ASSET_COLUMNS = setOf(UnifiedCsvFormat.ASSET, UnifiedCsvFormat.TO_ASSET, UnifiedCsvFormat.FEE_ASSET)
        val WALLET_COLUMNS = setOf(UnifiedCsvFormat.WALLET, UnifiedCsvFormat.TO_WALLET)
    }
}
