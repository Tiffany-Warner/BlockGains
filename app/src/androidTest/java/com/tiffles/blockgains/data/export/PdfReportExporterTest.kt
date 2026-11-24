package com.tiffles.blockgains.data.export

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test

/** M5 acceptance: the PDF renders a multi-page events table (needs the real PdfDocument). */
class PdfReportExporterTest {

    @Test
    fun manyEvents_renderOnSeveralPages() {
        val start = LocalDate.of(2024, 1, 1)
        val records = (0 until 120).flatMap { i ->
            listOf(
                tx(i * 2L + 1, TransactionType.BUY, start.plusDays(i.toLong()), price = "100"),
                tx(i * 2L + 2, TransactionType.SELL, start.plusDays(i.toLong() + 1), price = "110.55"),
            )
        }
        val result = TaxCalculator().calculate(records, CalcSettings("FAKE-WALLET-1"))
        val data = TaxYearData.from(result, records, 2024, AssetFilter.ALL)
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "report-test.pdf")

        file.outputStream().use { PdfReportExporter().write(data, LocalDate.of(2026, 11, 21), it) }

        val expectedPages = PdfPagination.pages(data.events.size, PdfReportExporter.FIRST_PAGE_ROWS, PdfReportExporter.OTHER_PAGE_ROWS).size
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> assertThat(renderer.pageCount).isEqualTo(expectedPages) }
        }
        assertThat(expectedPages).isAtLeast(3)
        file.delete()
    }

    @Test
    fun shortfallBanner_takesRowsFromPageOne() {
        // 78 events (77 pairs + the shortfall sale, which is an event since option B) fill exactly
        // two pages without a banner; the banner pushes the last rows to page 3.
        val start = LocalDate.of(2024, 1, 1)
        val pairs = (0 until 77).flatMap { i ->
            listOf(
                tx(i * 2L + 1, TransactionType.BUY, start.plusDays(i.toLong()), price = "100"),
                tx(i * 2L + 2, TransactionType.SELL, start.plusDays(i.toLong() + 1), price = "110.55"),
            )
        }
        val unmatched = tx(1_000, TransactionType.SELL, LocalDate.of(2024, 6, 1), price = "50").copy(asset = "FAKEC")
        val records = pairs + unmatched
        val result = TaxCalculator().calculate(records, CalcSettings("FAKE-WALLET-1"))
        val data = TaxYearData.from(result, records, 2024, AssetFilter.ALL)
        assertThat(data.shortfalls).hasSize(1)
        assertThat(data.events).hasSize(78)
        assertThat(PdfPagination.pages(data.events.size, PdfReportExporter.FIRST_PAGE_ROWS, PdfReportExporter.OTHER_PAGE_ROWS)).hasSize(2)
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "report-banner-test.pdf")

        file.outputStream().use { PdfReportExporter().write(data, LocalDate.of(2026, 11, 21), it) }

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> assertThat(renderer.pageCount).isEqualTo(3) }
        }
        file.delete()
    }

    private fun tx(id: Long, type: TransactionType, date: LocalDate, price: String) = Transaction(
        id = id, type = type, date = date, asset = "FAKE-LONG-NAME-TOKEN", wallet = "FAKE-WALLET-1",
        quantity = BigDecimal("1.5"), priceUsd = BigDecimal(price),
    )
}
