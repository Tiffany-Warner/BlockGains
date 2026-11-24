package com.tiffles.blockgains.ui.importexport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/** Export with no records: an empty state pointing to Import instead of export rows. */
class ExportEmptyStateTest {

    @get:Rule val compose = createComposeRule()

    private fun setContent(hasRecords: Boolean) {
        val export = ExportUiState(2026, emptyList(), AssetFilter.ALL, hasRecords = hasRecords, today = LocalDate.of(2026, 11, 21))
        compose.setContent {
            BlockGainsTheme(dynamicColor = false) { ImportExportContent(ImportUiState(), ImportActions(), export, ExportActions()) }
        }
        compose.onNodeWithText("Export").performClick()
    }

    @Test
    fun noRecords_showsTheEmptyState_keepsRestore_andImportOpensTheImportTab() {
        setContent(hasRecords = false)

        compose.onNodeWithText("Nothing to export yet").assertIsDisplayed()
        compose.onNodeWithText("Taxable events (CSV)").assertDoesNotExist()
        compose.onNodeWithText("Full backup (JSON)").assertDoesNotExist()
        compose.onNodeWithText("Restore from backup").assertIsDisplayed()

        compose.onNodeWithText("Import transactions").performClick()
        compose.onNodeWithText("Preview import").assertIsDisplayed()
        compose.onNodeWithText("Nothing to export yet").assertDoesNotExist()
    }

    @Test
    fun withRecords_showsTheExports() {
        setContent(hasRecords = true)

        compose.onNodeWithText("Taxable events (CSV)").assertIsDisplayed()
        compose.onNodeWithText("Nothing to export yet").assertDoesNotExist()
    }
}
