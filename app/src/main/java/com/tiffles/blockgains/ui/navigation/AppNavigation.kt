package com.tiffles.blockgains.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiffles.blockgains.ui.screen.CsvUploadScreen
import com.tiffles.blockgains.ui.screen.DashboardScreen
import com.tiffles.blockgains.ui.viewmodel.DashboardViewModel

sealed class Screen {
    object Upload : Screen()
    object Dashboard : Screen()
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Upload) }
    val dashboardViewModel: DashboardViewModel = viewModel()
    val context = LocalContext.current

    when (currentScreen) {
        is Screen.Upload -> {
            CsvUploadScreen(
                onFilesSelected = { buysUri, sellsUri ->
                    dashboardViewModel.loadCsvFiles(context, buysUri, sellsUri)
                    currentScreen = Screen.Dashboard
                }
            )
        }
        is Screen.Dashboard -> {
            val uiState by dashboardViewModel.uiState.collectAsState()

            DashboardScreen(
                uiState = uiState,
                onBackClick = { currentScreen = Screen.Upload },
                onYearSelected = { year -> dashboardViewModel.selectYear(year) }
            )
        }
    }
}
