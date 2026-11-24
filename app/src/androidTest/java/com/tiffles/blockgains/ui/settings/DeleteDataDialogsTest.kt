package com.tiffles.blockgains.ui.settings

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import org.junit.Rule
import org.junit.Test

/** Settings → Data: both confirmation steps, for both delete actions. */
class DeleteDataDialogsTest {

    @get:Rule val compose = createComposeRule()

    private val deletes = mutableListOf<DeleteScope>()

    private fun setSettings(count: Int = 1234) {
        val settings = AppSettings(defaultWallet = "FAKE-WALLET-1", walletLabels = setOf("FAKE-WALLET-1"))
        compose.setContent {
            BlockGainsTheme(dynamicColor = false) {
                SettingsContent(
                    SettingsUiState(settings, listOf("FAKE-WALLET-1"), transactionCount = count),
                    SettingsActions(onDelete = { deletes += it }),
                )
            }
        }
    }

    private fun open(button: String) = compose.onNodeWithText(button).performScrollTo().performClick()

    private val deleteButton get() = compose.onNodeWithContentDescription("Delete permanently")

    @Test
    fun deleteTransactions_warnsWithCount_thenDeleteStaysOffUntilDeleteIsTyped() {
        setSettings()
        open("Delete all transactions")
        compose.onNode(hasText("This deletes 1,234 transactions.", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("This can't be undone.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()

        deleteButton.assertIsNotEnabled()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("delete")
        deleteButton.assertIsNotEnabled()
        field.performTextClearance()
        field.performTextInput("DELETE ")
        deleteButton.assertIsNotEnabled()
        field.performTextClearance()
        field.performTextInput("DELETE")
        deleteButton.assertIsEnabled().performClick()

        assertThat(deletes).containsExactly(DeleteScope.TRANSACTIONS)
    }

    @Test
    fun deleteAllData_saysSettingsAreReset_andDeletesAllData() {
        setSettings(count = 1)
        open("Delete all data")
        compose.onNode(hasText("This deletes 1 transaction and resets all settings", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("DELETE")
        deleteButton.performClick()

        assertThat(deletes).containsExactly(DeleteScope.ALL_DATA)
    }

    @Test
    fun cancel_getsTheFirstKeyboardFocus_inBothSteps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        setSettings()
        open("Delete all transactions")
        // Opened from a tap: buttons can't take focus in touch mode. The first key press
        // (a real one, through the input system) moves focus into the dialog: Cancel.
        compose.onNodeWithText("Cancel").assertIsNotFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.waitForIdle()
        compose.onNodeWithText("Cancel").assertIsFocused()

        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.waitForIdle()
        compose.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    fun cancel_inEitherStep_changesNothing() {
        setSettings()
        open("Delete all data")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Continue").assertDoesNotExist()

        open("Delete all transactions")
        compose.onNodeWithText("Continue").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("DELETE")
        compose.onNodeWithText("Cancel").performClick()
        deleteButton.assertDoesNotExist()

        // Starting again begins at step 1, with nothing typed.
        open("Delete all transactions")
        compose.onNodeWithText("Continue").performClick()
        deleteButton.assertIsNotEnabled()
        assertThat(deletes).isEmpty()
    }

    @Test
    fun backUpFirst_callsTheBackupCallback_andKeepsTheDialogOpen() {
        var backups = 0
        var continued = false
        compose.setContent {
            BlockGainsTheme(dynamicColor = false) {
                DeleteWarningDialog(
                    scope = DeleteScope.TRANSACTIONS,
                    transactionCount = 5,
                    onBackUp = { backups++ },
                    onCancel = {},
                    onContinue = { continued = true },
                )
            }
        }
        compose.onNodeWithText("Back up first").performClick()
        assertThat(backups).isEqualTo(1)
        assertThat(continued).isFalse()
        compose.onNodeWithText("Continue").assertIsDisplayed()
    }
}
