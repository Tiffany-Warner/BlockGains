package com.tiffles.blockgains.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import org.junit.Rule
import org.junit.Test

/** About shows the license and opens the bundled text offline; Back closes it. */
class SettingsAboutTest {

    @get:Rule val compose = createComposeRule()

    private val licenseStart = hasText("GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007", substring = true)

    @Test
    fun viewLicense_opensBundledText_andBackCloses() {
        val settings = AppSettings(defaultWallet = "FAKE-WALLET-1", walletLabels = setOf("FAKE-WALLET-1"))
        compose.setContent {
            BlockGainsTheme(dynamicColor = false) { SettingsContent(SettingsUiState(settings, listOf("FAKE-WALLET-1")), SettingsActions()) }
        }

        compose.onNodeWithText("License: GPL-3.0-or-later").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View license").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(licenseStart).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(licenseStart).assertIsDisplayed()

        // Send Back to the license dialog's own window. Espresso waits until that window has focus;
        // a plain pressBack() goes to whichever window is focused, which can still be the activity.
        onView(isRoot()).inRoot(isDialog()).perform(pressBack())
        compose.waitUntil(5_000) { compose.onAllNodes(licenseStart).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("View license").assertIsDisplayed()
    }
}
