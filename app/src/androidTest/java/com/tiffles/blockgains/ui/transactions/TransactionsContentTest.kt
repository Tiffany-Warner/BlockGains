package com.tiffles.blockgains.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.ui.preview.PreviewData
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import org.junit.Rule
import org.junit.Test

/** Landscape-sized screen: the header scrolls with the list, so rows are reachable on both tabs. */
class TransactionsContentTest {

    @get:Rule val compose = createComposeRule()

    private fun setContent(tab: TransactionsTab) {
        val state = TransactionsUiState.Content(
            year = 2024,
            years = listOf(2024),
            assetFilter = AssetFilter.ALL,
            filters = TransactionFilters(tab = tab),
            events = PreviewData.result.taxableEvents.filter { it.date.year == 2024 }.sortedByDescending { it.date },
            records = PreviewData.transactions.sortedByDescending { it.date },
            hasAnyRecords = true,
            assets = listOf("FAKEB"),
            wallets = listOf("FAKE-WALLET-1"),
        )
        compose.setContent {
            BlockGainsTheme(dynamicColor = false) {
                // Roughly a phone in landscape after the system bars and bottom navigation.
                Box(Modifier.width(800.dp).height(260.dp)) { TransactionsContent(state, TransactionsActions()) }
            }
        }
    }

    @Test
    fun allTransactions_headerScrollsAway_andRowsShow() {
        setContent(TransactionsTab.ALL)
        // The oldest record is the last row.
        compose.onNode(hasScrollToKeyAction()).performScrollToNode(hasText("Jan 10, 2023", substring = true))
        compose.onNode(hasText("Jan 10, 2023", substring = true)).assertIsDisplayed()
        assertThat(compose.onNodeWithText("All transactions").isNotDisplayed()).isTrue()
    }

    @Test
    fun taxableEvents_headerScrollsAway_andRowsShow() {
        setContent(TransactionsTab.EVENTS)
        compose.onNode(hasScrollToKeyAction()).performScrollToNode(hasText("Mar 1, 2024", substring = true))
        compose.onNode(hasText("Mar 1, 2024", substring = true)).assertIsDisplayed()
        assertThat(compose.onNodeWithText("Taxable events").isNotDisplayed()).isTrue()
    }
}
