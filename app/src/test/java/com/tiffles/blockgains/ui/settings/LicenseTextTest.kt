package com.tiffles.blockgains.ui.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class LicenseTextTest {

    private fun read(path: String) = File(path).readText().replace("\r\n", "\n")

    @Test
    fun bundledCopy_isIdenticalToRepoLicense() {
        // Unit tests run in the app module, so the repo root is one level up.
        assertThat(read("src/main/res/raw/license.txt")).isEqualTo(read("../LICENSE"))
    }

    @Test
    fun reflow_joinsLinesWithinParagraphs_andKeepsEveryWord() {
        val raw = "  TITLE\n  Version 3\n\nFirst line of a\nparagraph.\r\n\r\nSecond.\n"
        assertThat(LicenseText.reflow(raw)).isEqualTo("TITLE Version 3\n\nFirst line of a paragraph.\n\nSecond.")

        val license = read("../LICENSE")
        val words = { text: String -> text.split(Regex("""\s+""")).filter { it.isNotEmpty() } }
        assertThat(words(LicenseText.reflow(license))).isEqualTo(words(license))
    }
}
