package com.tiffles.blockgains.ui.importexport

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import java.time.LocalDate

@Composable
fun ImportExportScreen(
    onOpenPreview: () -> Unit,
    onOpenOpeningPositions: () -> Unit,
    onOpenIssues: () -> Unit,
    importViewModel: ImportViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val exportState by exportViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(importViewModel) { importViewModel.openPreview.collect { onOpenPreview() } }
    ImportExportContent(
        importState = importState,
        importActions = ImportActions(
            onPick = importViewModel::pickFile,
            onPreview = importViewModel::preview,
            onSaveTemplate = importViewModel::saveTemplate,
            onOpenOpeningPositions = onOpenOpeningPositions,
        ),
        exportState = exportState,
        exportActions = ExportActions(
            onYear = exportViewModel::selectYear,
            onFilter = exportViewModel::selectFilter,
            onExport = exportViewModel::export,
            onRestorePicked = exportViewModel::loadRestore,
            onConfirmRestore = exportViewModel::confirmRestore,
            onDismissRestore = exportViewModel::dismissRestore,
            onReviewIssues = onOpenIssues,
        ),
    )
}

/** One destination, two tabs (ui-guidelines → Navigation). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportContent(
    importState: ImportUiState,
    importActions: ImportActions,
    exportState: ExportUiState?,
    exportActions: ExportActions,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_import_export)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_import)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_export)) })
            }
            val content = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            when {
                tab == 0 -> ImportSection(importState, importActions, content)
                exportState == null -> LoadingState()
                else -> ExportSection(exportState, exportActions.copy(onImport = { tab = 0 }), content)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ImportExportPreview() {
    val state = ImportUiState(file = PickedFile(Uri.EMPTY, "fake-records.csv"))
    val export = ExportUiState(2024, listOf(2024, 2023), AssetFilter.ALL, hasRecords = true, today = LocalDate.of(2026, 11, 21))
    BlockGainsTheme(dynamicColor = false) { ImportExportContent(state, ImportActions(), export, ExportActions()) }
}
