package com.tiffles.blockgains.ui.format

import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.R
import java.math.BigDecimal
import org.junit.Test

/** Counts followed by a noun read "1 unit", "1 lot", "2 units" (English resources). */
class PluralStringsTest {

    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun openingLotBody_isSingularOnlyForExactlyOneUnit() {
        fun body(quantity: String) = res.getQuantityString(
            R.plurals.opening_lot_body,
            QuantityFormatter.pluralCount(BigDecimal(quantity)),
            QuantityFormatter.full(BigDecimal(quantity)),
            "$20,000.00",
        )
        assertThat(body("1")).isEqualTo("1 unit · basis $20,000.00")
        assertThat(body("0.5")).isEqualTo("0.5 units · basis $20,000.00")
        assertThat(body("3")).isEqualTo("3 units · basis $20,000.00")
    }

    @Test
    fun previewTilesAndMatchedLots_followTheCount() {
        assertThat(res.getQuantityString(R.plurals.preview_duplicates_tile, 1)).isEqualTo("Duplicate skipped")
        assertThat(res.getQuantityString(R.plurals.preview_duplicates_tile, 2)).isEqualTo("Duplicates skipped")
        assertThat(res.getQuantityString(R.plurals.preview_problems_tile, 1)).isEqualTo("Row with problems")
        assertThat(res.getQuantityString(R.plurals.preview_problems_tile, 0)).isEqualTo("Rows with problems")
        assertThat(res.getQuantityString(R.plurals.matched_lots, 1, 1)).isEqualTo("Matched lot (1)")
        assertThat(res.getQuantityString(R.plurals.matched_lots, 2, 2)).isEqualTo("Matched lots (2)")
    }
}
