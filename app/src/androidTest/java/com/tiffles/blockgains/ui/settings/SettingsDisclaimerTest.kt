package com.tiffles.blockgains.ui.settings

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import org.junit.Rule
import org.junit.Test

/** Settings opens with the not-tax-advice card, read as one item, and says it only once. */
class SettingsDisclaimerTest {

    @get:Rule val compose = createComposeRule()

    private val title = "Estimate for personal records, not tax advice."
    private val body = "Check results against your 1099-DA or 1099-B and a tax professional before filing."

    @Test
    fun disclaimerCard_isTheFirstItem_andReadAsOneNode() {
        val settings = AppSettings(defaultWallet = "FAKE-WALLET-1", walletLabels = setOf("FAKE-WALLET-1"))
        compose.setContent {
            BlockGainsTheme(dynamicColor = false) {
                SettingsContent(SettingsUiState(settings, listOf("FAKE-WALLET-1"), transactionCount = 3), SettingsActions())
            }
        }

        compose.onNode(hasScrollAction()).onChildAt(0).assert(hasTestTag(DISCLAIMER_TAG))
        // Merged: one node carries both the title and the body.
        compose.onNodeWithTag(DISCLAIMER_TAG)
            .assertIsDisplayed()
            .assert(hasText(title))
            .assert(hasText(body))
        // Not repeated in About.
        compose.onAllNodesWithText(title).assertCountEquals(1)
    }
}
