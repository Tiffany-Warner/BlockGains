package com.tiffles.blockgains.ui.settings

/**
 * The GPL text (res/raw/license.txt, a copy of the repo's LICENSE) is hard-wrapped at about 72
 * columns, which wraps badly on a phone. For display only, join each paragraph's lines so the
 * text reflows to the screen width. Paragraphs stay separated by blank lines; the words don't change.
 */
object LicenseText {
    fun reflow(raw: String): String =
        raw.replace("\r\n", "\n")
            .split(Regex("""\n\s*\n"""))
            .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
}
