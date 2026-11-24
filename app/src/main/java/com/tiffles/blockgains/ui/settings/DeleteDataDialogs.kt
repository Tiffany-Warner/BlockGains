package com.tiffles.blockgains.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.tiffles.blockgains.R
import com.tiffles.blockgains.ui.format.CountFormatter
import com.tiffles.blockgains.ui.theme.BlockGainsTheme

/**
 * Step 1 of a Settings → Data delete: what goes, how many records, that it can't be undone,
 * and a way to back up first. Cancel has the initial keyboard focus; nothing changes until step 2.
 */
@Composable
fun DeleteWarningDialog(
    scope: DeleteScope,
    transactionCount: Int,
    onBackUp: () -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
) {
    val count = CountFormatter.format(transactionCount)
    val cancelFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.focusEntersAt(cancelFocus),
        title = {
            Text(stringResource(if (scope == DeleteScope.TRANSACTIONS) R.string.delete_transactions_title else R.string.delete_all_title))
        },
        text = {
            Column {
                val body = if (scope == DeleteScope.TRANSACTIONS) R.plurals.delete_transactions_body else R.plurals.delete_all_body
                Text(pluralStringResource(body, transactionCount, count))
                Text(stringResource(R.string.delete_cannot_undo), Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                val backUpCd = stringResource(R.string.delete_back_up_first_cd)
                OutlinedButton(onClick = onBackUp, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Outlined.Backup, contentDescription = null)
                    // TalkBack reads this instead of the short visible label.
                    Text(stringResource(R.string.delete_back_up_first), Modifier.padding(start = 8.dp).semantics { contentDescription = backUpCd })
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.action_continue)) } },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Step 2: Delete stays off until the user types the confirmation word exactly (case-sensitive). Cancel has the initial keyboard focus. */
@Composable
fun DeleteTypeToConfirmDialog(onCancel: () -> Unit, onDelete: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val word = stringResource(R.string.delete_confirm_word)
    val deleteCd = stringResource(R.string.delete_button_cd)
    val cancelFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.focusEntersAt(cancelFocus),
        title = { Text(stringResource(R.string.delete_type_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_type_body), Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    // The label is the field's TalkBack label; the instruction above is read too.
                    label = { Text(stringResource(R.string.delete_type_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                enabled = typed == word,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.action_delete), Modifier.semantics { contentDescription = deleteCd }) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Keyboard focus enters the dialog at [requester] (its Cancel button). Buttons can't take focus in
 * touch mode, so nothing is focused after a tap; the first key press moves focus into the dialog,
 * and this sends it to Cancel instead of the first button.
 */
private fun Modifier.focusEntersAt(requester: FocusRequester): Modifier =
    focusProperties { onEnter = { requester.requestFocus() } }.focusGroup()

@PreviewLightDark
@Composable
private fun DeleteWarningPreview() {
    BlockGainsTheme(dynamicColor = false) {
        DeleteWarningDialog(DeleteScope.ALL_DATA, transactionCount = 1234, onBackUp = {}, onCancel = {}, onContinue = {})
    }
}

@PreviewLightDark
@Composable
private fun DeleteTypePreview() {
    BlockGainsTheme(dynamicColor = false) {
        DeleteTypeToConfirmDialog(onCancel = {}, onDelete = {})
    }
}
