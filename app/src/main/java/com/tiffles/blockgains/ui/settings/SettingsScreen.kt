package com.tiffles.blockgains.ui.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.blockgains.BuildConfig
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.ui.components.BackButton
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.components.SectionHeader
import com.tiffles.blockgains.ui.components.WalletField
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsActions(
    val onDefaultWallet: (String) -> Unit = {},
    /** Returns false if the label is taken or blank. */
    val onAddWallet: (String) -> Boolean = { true },
    val onRenameWallet: (from: String, to: String) -> Boolean = { _, _ -> true },
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    /** Writes a Full backup (JSON) to the picked document. */
    val onBackUp: (Uri) -> Unit = {},
    /** Runs only after both confirmation steps. */
    val onDelete: (DeleteScope) -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onDeleted: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onDeleted() } }
    SettingsContent(
        state,
        SettingsActions(
            onDefaultWallet = viewModel::setDefaultWallet,
            onAddWallet = viewModel::addWallet,
            onRenameWallet = viewModel::renameWallet,
            onThemeMode = viewModel::setThemeMode,
            onDynamicColor = viewModel::setDynamicColor,
            onBackUp = viewModel::backup,
            onDelete = viewModel::delete,
            onBack = onBack,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsUiState?, actions: SettingsActions) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.action_settings)) }, navigationIcon = { BackButton(actions.onBack) }) },
    ) { padding ->
        if (state == null) {
            LoadingState(Modifier.padding(padding))
        } else {
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                DisclaimerCard(Modifier.padding(top = 8.dp, bottom = 8.dp))
                DefaultWalletSection(state, actions)
                WalletsSection(state.wallets, actions)
                ThemeSection(state.settings, actions)
                AboutSection()
                DataSection(state, actions)
            }
        }
    }
}

@Composable
private fun DefaultWalletSection(state: SettingsUiState, actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_default_wallet))
    Text(stringResource(R.string.settings_default_wallet_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    WalletField(state.settings.defaultWallet, state.wallets, actions.onDefaultWallet, Modifier.padding(top = 8.dp))
}

@Composable
private fun WalletsSection(wallets: List<String>, actions: SettingsActions) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.settings_wallets))
    wallets.forEach { wallet ->
        ListItem(
            headlineContent = { Text(wallet) },
            trailingContent = {
                IconButton(onClick = { renaming = wallet }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.settings_rename_cd, wallet))
                }
            },
        )
    }
    TextButton(onClick = { adding = true }) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text(stringResource(R.string.settings_add_wallet), Modifier.padding(start = 8.dp))
    }
    renaming?.let { from ->
        WalletLabelDialog(
            title = stringResource(R.string.settings_rename_wallet),
            body = stringResource(R.string.settings_rename_body),
            initial = from,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { actions.onRenameWallet(from, it) },
            onDismiss = { renaming = null },
        )
    }
    if (adding) {
        WalletLabelDialog(
            title = stringResource(R.string.settings_add_wallet),
            body = null,
            initial = "",
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = actions.onAddWallet,
            onDismiss = { adding = false },
        )
    }
}

/** [onConfirm] returns false when the label is unavailable; the dialog then stays open with an error. */
@Composable
private fun WalletLabelDialog(
    title: String,
    body: String?,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                body?.let { Text(it, Modifier.padding(bottom = 12.dp)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = false
                    },
                    label = { Text(stringResource(R.string.settings_wallet_label)) },
                    supportingText = {
                        Text(stringResource(if (error) R.string.settings_wallet_exists else R.string.settings_wallet_hint))
                    },
                    isError = error,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { if (onConfirm(text)) onDismiss() else error = true },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ThemeSection(settings: AppSettings, actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_theme))
    val options = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
    Column(Modifier.selectableGroup()) {
        options.forEach { (mode, label) ->
            ListItem(
                modifier = Modifier.selectable(selected = settings.themeMode == mode, onClick = { actions.onThemeMode(mode) }, role = Role.RadioButton),
                leadingContent = { RadioButton(selected = settings.themeMode == mode, onClick = null) },
                headlineContent = { Text(stringResource(label)) },
            )
        }
    }
    // Wallpaper colors exist only on Android 12+; older devices don't see the switch at all.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ListItem(
            modifier = Modifier.toggleable(value = settings.dynamicColor, role = Role.Switch, onValueChange = actions.onDynamicColor),
            headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
            supportingContent = { Text(stringResource(R.string.settings_dynamic_color_body)) },
            trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = null) },
        )
    }
}

/**
 * First thing on Settings: the app's estimates aren't tax advice. Tertiary container colors
 * (not error red: nothing is wrong); TalkBack reads the card as one item.
 */
@Composable
private fun DisclaimerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag(DISCLAIMER_TAG).semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_disclaimer_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Test tag of [DisclaimerCard]. */
const val DISCLAIMER_TAG = "settings-disclaimer"

@Composable
private fun AboutSection() {
    var showLicense by rememberSaveable { mutableStateOf(false) }
    if (showLicense) LicenseDialog(onDismiss = { showLicense = false })
    SectionHeader(stringResource(R.string.settings_about))
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_license), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { showLicense = true }) { Text(stringResource(R.string.settings_view_license)) }
    }
    HorizontalDivider()
}

/** Settings → Data: two destructive actions, each behind [DeleteWarningDialog] then [DeleteTypeToConfirmDialog]. */
@Composable
private fun DataSection(state: SettingsUiState, actions: SettingsActions) {
    var pending by rememberSaveable { mutableStateOf<DeleteScope?>(null) }
    var typing by rememberSaveable { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(actions.onBackUp)
    }
    val cancel = {
        pending = null
        typing = false
    }

    SectionHeader(stringResource(R.string.settings_data))
    Text(stringResource(R.string.settings_data_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
        DestructiveButton(stringResource(R.string.delete_transactions_button), stringResource(R.string.delete_transactions_button_cd)) {
            pending = DeleteScope.TRANSACTIONS
        }
        DestructiveButton(stringResource(R.string.delete_all_button), stringResource(R.string.delete_all_button_cd)) {
            pending = DeleteScope.ALL_DATA
        }
    }

    val scope = pending ?: return
    if (!typing) {
        DeleteWarningDialog(
            scope = scope,
            transactionCount = state.transactionCount,
            onBackUp = { backupLauncher.launch(state.backupFileName) },
            onCancel = cancel,
            onContinue = { typing = true },
        )
    } else {
        DeleteTypeToConfirmDialog(
            onCancel = cancel,
            onDelete = {
                cancel()
                actions.onDelete(scope)
            },
        )
    }
}

@Composable
private fun DestructiveButton(text: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) { Text(text, Modifier.semantics { contentDescription = description }) }
}

/** Full-screen, offline view of the bundled GPL text (the app has no INTERNET permission). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val text by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) {
            LicenseText.reflow(resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() })
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.license_title)) }, navigationIcon = { BackButton(onDismiss) }) },
        ) { padding ->
            val license = text
            if (license == null) {
                LoadingState(Modifier.padding(padding))
            } else {
                Text(
                    license,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    val settings = AppSettings(defaultWallet = "FAKE-WALLET-1", walletLabels = setOf("FAKE-WALLET-1", "FAKE-EXCHANGE-1"))
    BlockGainsTheme(dynamicColor = false) {
        SettingsContent(SettingsUiState(settings, listOf("FAKE-EXCHANGE-1", "FAKE-WALLET-1", "FAKE-WALLET-2")), SettingsActions())
    }
}
