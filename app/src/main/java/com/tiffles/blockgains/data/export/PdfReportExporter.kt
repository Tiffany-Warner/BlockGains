package com.tiffles.blockgains.data.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.TaxableEvent
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** Splits table rows into pages: the first page holds fewer rows (it carries the title and totals). */
object PdfPagination {
    fun pages(rowCount: Int, firstPageRows: Int, otherPageRows: Int): List<IntRange> {
        require(firstPageRows > 0 && otherPageRows > 0)
        if (rowCount <= firstPageRows) return listOf(0 until rowCount)
        val pages = mutableListOf(0 until firstPageRows)
        var start = firstPageRows
        while (start < rowCount) {
            val end = minOf(start + otherPageRows, rowCount)
            pages += start until end
            start = end
        }
        return pages
    }
}

/**
 * Page-1 warning for unresolved shortfalls (tax-rules → Open questions, partial shortfall).
 * Pure text so it's testable on the JVM; [PdfReportExporter] draws it as a red box.
 */
object PdfShortfallBanner {
    const val MAX_DETAIL_LINES = 5

    /** Empty when there's nothing to warn about. First line is the headline, then one line per issue. */
    fun lines(shortfalls: List<CalcIssue.InsufficientLots>, year: Int): List<String> {
        if (shortfalls.isEmpty()) return emptyList()
        val count = shortfalls.size
        val noun = if (count == 1) "shortfall" else "shortfalls"
        val details = shortfalls.take(MAX_DETAIL_LINES).map {
            "${it.date}  ${it.type.name}${if (it.networkFee) " fee" else ""}  ${it.asset}  ${it.wallet}${if (it.pooled) " (all wallets pooled)" else ""}  short by ${ExportFormat.quantity(it.shortBy)}"
        }
        val more = (count - MAX_DETAIL_LINES).takeIf { it > 0 }?.let { listOf("…and $it more. See Issues in the app.") }.orEmpty()
        return listOf(
            "Warning: $count unresolved $noun in your records up to $year.",
            "Some sales, swaps, transfers or adjustments used more units than were available. Units without a matching lot count as short-term with $0 basis, so gains may be overstated.",
        ) + details + more
    }

    /**
     * Greedy word wrap to [maxWidth] as measured by [measure], so no banner text is cut off.
     * A single word wider than the line stays whole on its own line.
     */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        text.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && measure(candidate.trimEnd()) > maxWidth) {
                lines += line.trimEnd()
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotBlank()) lines += line.trimEnd()
        return lines
    }
}

/**
 * Export 5: a plain, printable year report (ui-guidelines → Export): title, year, generation
 * date, a red shortfall warning when needed, ST/LT/net/income totals, a paginated taxable-events
 * table and the disclaimer on every page.
 * English only, like the CSV headers: it's a document format, not app UI.
 */
class PdfReportExporter @Inject constructor() {

    fun write(data: TaxYearData, generatedOn: LocalDate, out: OutputStream) {
        val document = PdfDocument()
        try {
            val banner = PdfShortfallBanner.lines(data.shortfalls, data.year).flatMapIndexed { i, line ->
                val paint = if (i == 0) bannerTitle else bannerText
                PdfShortfallBanner.wrap(line, BANNER_TEXT_WIDTH) { paint.measureText(it) }.map { BannerLine(it, headline = i == 0) }
            }
            val pages = PdfPagination.pages(data.events.size, firstPageRows(banner.size), OTHER_PAGE_ROWS)
            pages.forEachIndexed { index, rows ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                val canvas = page.canvas
                var y = MARGIN
                if (index == 0) y = drawHeader(canvas, data, generatedOn, banner, y)
                drawTable(canvas, data.events.subList(rows.first.coerceAtLeast(0), rows.last + 1), y, emptyNote = index == 0 && data.events.isEmpty())
                drawFooter(canvas, index + 1, pages.size)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun drawHeader(canvas: Canvas, data: TaxYearData, generatedOn: LocalDate, banner: List<BannerLine>, top: Float): Float {
        var y = top + 18f
        canvas.drawText("BlockGains capital gains report: ${data.year}", MARGIN, y, title)
        y += 18f
        val scope = when (data.filter) {
            AssetFilter.ALL -> "All asset classes"
            AssetFilter.CRYPTO -> "Crypto only"
            AssetFilter.STOCK -> "Stocks only"
        }
        canvas.drawText("Generated $generatedOn · $scope · USD", MARGIN, y, small)
        y += 12f
        y = drawBanner(canvas, banner, y)
        y += 12f
        listOf(
            "Short-term gain/loss" to money(data.shortTerm, signed = true),
            "Long-term gain/loss" to money(data.longTerm, signed = true),
            "Net capital gain/loss" to money(data.net, signed = true),
            "Ordinary income (staking, rewards)" to money(data.ordinaryIncome),
        ).forEach { (label, value) ->
            canvas.drawText(label, MARGIN, y, body)
            drawRight(canvas, value, MARGIN + 300f, y, mono)
            y += 15f
        }
        if (data.net.signum() < 0) {
            canvas.drawText("Net loss: up to \$3,000 (\$1,500 married filing separately) offsets other income; the rest carries forward.", MARGIN, y, small)
            y += 15f
        }
        return y + 12f
    }

    /** Red box under the title; returns the y below it (unchanged when [lines] is empty). Lines are already wrapped. */
    private fun drawBanner(canvas: Canvas, lines: List<BannerLine>, top: Float): Float {
        if (lines.isEmpty()) return top
        val height = bannerHeight(lines.size)
        canvas.drawRect(MARGIN, top, PAGE_WIDTH - MARGIN, top + height, bannerFill)
        canvas.drawRect(MARGIN, top, PAGE_WIDTH - MARGIN, top + height, bannerBorder)
        var y = top + BANNER_PADDING + 9f
        lines.forEach { line ->
            val paint = if (line.headline) bannerTitle else bannerText
            canvas.drawText(fit(line.text, BANNER_TEXT_WIDTH, paint), MARGIN + BANNER_PADDING, y, paint)
            y += BANNER_LINE_HEIGHT
        }
        return top + height
    }

    private fun drawTable(canvas: Canvas, events: List<TaxableEvent>, top: Float, emptyNote: Boolean) {
        var y = top + ROW_HEIGHT
        COLUMNS.forEach { column -> drawCell(canvas, column.title, column, y, header) }
        canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, rule)
        if (emptyNote) {
            canvas.drawText("No taxable events in this year.", MARGIN, y + ROW_HEIGHT + 4f, body)
            return
        }
        events.forEach { event ->
            y += ROW_HEIGHT
            val cells = listOf(
                event.date.toString(), if (event.isTransferFee) "${event.asset} (fee)" else event.asset, ExportFormat.quantity(event.quantity),
                money(event.proceeds), money(event.basis), money(event.gain, signed = true), termText(event.term),
            )
            COLUMNS.zip(cells).forEach { (column, text) -> drawCell(canvas, text, column, y, if (column.numeric) mono else body) }
        }
    }

    private fun drawFooter(canvas: Canvas, page: Int, pages: Int) {
        val y = PAGE_HEIGHT - MARGIN + 16f
        canvas.drawText("Estimate for personal records, not tax advice.", MARGIN, y, small)
        drawRight(canvas, "Page $page of $pages", PAGE_WIDTH - MARGIN, y, small)
    }

    private fun drawCell(canvas: Canvas, text: String, column: Column, y: Float, paint: Paint) {
        val fitted = fit(text, column.width - CELL_PADDING, paint)
        if (column.numeric) drawRight(canvas, fitted, column.x + column.width - CELL_PADDING, y, paint) else canvas.drawText(fitted, column.x, y, paint)
    }

    private fun drawRight(canvas: Canvas, text: String, right: Float, y: Float, paint: Paint) =
        canvas.drawText(text, right - paint.measureText(text), y, paint)

    /** Truncates with an ellipsis so long asset names never overlap the next column. */
    private fun fit(text: String, width: Float, paint: Paint): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + ELLIPSIS) > width) end--
        return text.substring(0, end) + ELLIPSIS
    }

    private fun termText(term: HoldingTerm?) = when (term) {
        HoldingTerm.SHORT -> "Short"
        HoldingTerm.LONG -> "Long"
        null -> "Mixed"
    }

    private data class BannerLine(val text: String, val headline: Boolean)

    private data class Column(val title: String, val x: Float, val width: Float, val numeric: Boolean)

    private val title = paint(16f, Typeface.DEFAULT_BOLD)
    private val header = paint(9f, Typeface.DEFAULT_BOLD)
    private val body = paint(9f, Typeface.DEFAULT)
    private val small = paint(8f, Typeface.DEFAULT)
    private val mono = paint(9f, Typeface.MONOSPACE)
    private val rule = Paint().apply { strokeWidth = 0.5f }
    private val bannerFill = Paint().apply { color = 0xFFFDECEA.toInt() }
    private val bannerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BANNER_RED
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val bannerTitle = paint(9f, Typeface.DEFAULT_BOLD).apply { color = BANNER_RED }
    private val bannerText = paint(8f, Typeface.DEFAULT).apply { color = BANNER_RED }

    private fun paint(size: Float, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = face
    }

    companion object {
        /** US Letter in PostScript points. */
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792
        private const val MARGIN = 48f
        private const val ROW_HEIGHT = 15f
        private const val CELL_PADDING = 10f
        private const val ELLIPSIS = "…"
        const val FIRST_PAGE_ROWS = 34
        const val OTHER_PAGE_ROWS = 44
        private const val BANNER_LINE_HEIGHT = 12f
        private const val BANNER_PADDING = 8f
        private val BANNER_RED = 0xFFB3261E.toInt()

        private const val BANNER_TEXT_WIDTH = PAGE_WIDTH - 2 * (MARGIN + BANNER_PADDING)

        /**
         * `$1,234.56`, with a true minus (U+2212) when negative, like the app (ui-guidelines → Numbers).
         * [signed] adds `+` to gains; zero (also after rounding) has no sign.
         */
        fun money(value: BigDecimal, signed: Boolean = false): String {
            val rounded = ExportFormat.money(value.abs())
            val grouped = rounded.substringBefore('.').reversed().chunked(3).joinToString(",").reversed() + "." + rounded.substringAfter('.')
            val nonZero = rounded.any { it in '1'..'9' }
            val sign = when {
                !nonZero -> ""
                value.signum() < 0 -> "−"
                signed -> "+"
                else -> ""
            }
            return "$sign\$$grouped"
        }

        private fun bannerHeight(lines: Int): Float = lines * BANNER_LINE_HEIGHT + 2 * BANNER_PADDING

        /** Page 1 gives up the rows the banner (plus its gap) covers, so the table never runs into the footer. */
        fun firstPageRows(bannerLines: Int): Int {
            if (bannerLines == 0) return FIRST_PAGE_ROWS
            val rowsTaken = kotlin.math.ceil((bannerHeight(bannerLines) + 12f) / ROW_HEIGHT).toInt()
            return FIRST_PAGE_ROWS - rowsTaken
        }

        private val COLUMNS: List<Column> = run {
            val specs = listOf(
                Triple("Date sold", 64f, false), Triple("Asset", 92f, false), Triple("Quantity", 86f, true),
                Triple("Proceeds", 72f, true), Triple("Basis", 72f, true), Triple("Gain/loss", 76f, true), Triple("Term", 54f, false),
            )
            var x = MARGIN
            specs.map { (name, width, numeric) -> Column(name, x, width, numeric).also { x += width } }
        }
    }
}
