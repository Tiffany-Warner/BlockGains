package com.tiffles.blockgains.ui.summary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.usecase.YearSummary
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test

/** Stateless SummaryContent with fixed states and fake callbacks (coding-standards → Testing). */
class SummaryContentTest {

    @get:Rule val compose = createComposeRule()

    private val targets = mutableListOf<SummaryTarget>()

    private fun summary(st: String, lt: String, issues: List<CalcIssue> = emptyList()) = YearSummary(
        year = 2024,
        shortTerm = BigDecimal(st),
        longTerm = BigDecimal(lt),
        ordinaryIncome = BigDecimal("50"),
        eventCount = 3,
        issues = issues,
    )

    private fun show(state: SummaryUiState) {
        compose.setContent { BlockGainsTheme(dynamicColor = false) { SummaryContent(state, {}, {}, { targets += it }) } }
    }

    @Test
    fun empty_offersImport() {
        show(SummaryUiState.Empty)
        compose.onNodeWithText("Import transactions").performClick()
        assertThat(targets).containsExactly(SummaryTarget.Import)
    }

    @Test
    fun content_showsNetAndBreakdown_andCardsOpenTransactions() {
        show(SummaryUiState.Content(listOf(2024), AssetFilter.ALL, summary(st = "-400", lt = "1000")))

        compose.onNodeWithText("Net capital gain").assertIsDisplayed()
        compose.onNodeWithText("+$600.00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Short-term, −$400.00. Show transactions").performClick()
        compose.onNodeWithContentDescription("Long-term, +$1,000.00. Show transactions").performClick()
        assertThat(targets).containsExactly(SummaryTarget.Events(HoldingTerm.SHORT), SummaryTarget.Events(HoldingTerm.LONG)).inOrder()
    }

    @Test
    fun netLoss_showsLossLimitNote() {
        show(SummaryUiState.Content(listOf(2024), AssetFilter.ALL, summary(st = "-5000", lt = "1000")))
        compose.onNodeWithText("Net capital loss").assertIsDisplayed()
        compose.onNodeWithText("A net capital loss offsets at most", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun issues_showBannerThatOpensIssues() {
        val issue = CalcIssue.InvalidRow(InvalidReason.MISSING_VALUE, transactionId = 1)
        show(SummaryUiState.Content(listOf(2024), AssetFilter.ALL, summary(st = "0", lt = "0", issues = listOf(issue))))

        compose.onNodeWithText("1 issue needs attention").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        assertThat(targets).containsExactly(SummaryTarget.Issues)
    }
}
