package com.tiffles.blockgains.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.ui.importexport.ImportExportScreen
import com.tiffles.blockgains.ui.importexport.ImportPreviewScreen
import com.tiffles.blockgains.ui.navigation.Route
import com.tiffles.blockgains.ui.navigation.closeImportPreview
import com.tiffles.blockgains.ui.navigation.navigateTopLevel
import com.tiffles.blockgains.ui.openingpositions.OpeningPositionsScreen
import com.tiffles.blockgains.ui.settings.SettingsScreen
import com.tiffles.blockgains.ui.summary.IssuesScreen
import com.tiffles.blockgains.ui.summary.SummaryScreen
import com.tiffles.blockgains.ui.summary.SummaryTarget
import com.tiffles.blockgains.ui.transactions.EventDetailScreen
import com.tiffles.blockgains.ui.transactions.TransactionDetailScreen
import com.tiffles.blockgains.ui.transactions.TransactionsScreen
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

private data class TopLevel(val route: Route, val routeClass: KClass<out Route>, val label: Int, val icon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(Route.Summary, Route.Summary::class, R.string.nav_summary, Icons.Outlined.Insights),
    TopLevel(Route.Transactions(), Route.Transactions::class, R.string.nav_transactions, Icons.AutoMirrored.Outlined.ReceiptLong),
    TopLevel(Route.ImportExport, Route.ImportExport::class, R.string.nav_import_export, Icons.Outlined.ImportExport),
)

/** App shell: bottom navigation (Summary, Transactions, Import / Export), nav graph and snackbar. */
@Composable
fun BlockGainsAppRoot(messages: Flow<UserMessage>) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(messages) {
        messages.collect { message ->
            val text = when (message) {
                is UserMessage.Text -> resources.getString(message.res, *message.args.toTypedArray())
                is UserMessage.Plural -> resources.getQuantityString(message.res, message.count, message.count)
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TOP_LEVEL.any { destination.isOn(it.routeClass) }

    Scaffold(
        bottomBar = { if (showBottomBar) BottomBar(destination) { navController.navigateTopLevel(it) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Each screen's own Scaffold handles the status bar; this one only reserves the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Summary,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) { graph(navController) }
    }
}

@Composable
private fun BottomBar(destination: NavDestination?, onSelect: (Route) -> Unit) {
    NavigationBar {
        TOP_LEVEL.forEach { item ->
            NavigationBarItem(
                selected = destination.isOn(item.routeClass),
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                // Up to two lines, so large font scales wrap instead of cutting the label; a long word hyphenates.
                label = {
                    Text(
                        stringResource(item.label),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(hyphens = Hyphens.Auto),
                    )
                },
            )
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.graph(nav: NavHostController) {
    composable<Route.Summary> {
        SummaryScreen(onNavigate = { target -> nav.openFromSummary(target) })
    }
    composable<Route.Transactions> {
        TransactionsScreen(
            onEvent = { nav.navigate(Route.EventDetail(it)) },
            onTransaction = { nav.navigate(Route.TransactionDetail(it)) },
            onImport = { nav.navigateTopLevel(Route.ImportExport) },
        )
    }
    composable<Route.ImportExport> {
        ImportExportScreen(
            onOpenPreview = { nav.navigate(Route.ImportPreview) },
            onOpenOpeningPositions = { nav.navigate(Route.OpeningPositions) },
            onOpenIssues = { nav.navigate(Route.Issues) },
        )
    }
    composable<Route.Settings> {
        // After a delete: back to Summary (Settings opens from there), which is now empty.
        SettingsScreen(onBack = nav::navigateUp, onDeleted = { nav.navigateTopLevel(Route.Summary) })
    }
    composable<Route.Issues> {
        IssuesScreen(onBack = nav::navigateUp, onOpenOpeningPositions = { nav.navigate(Route.OpeningPositions) })
    }
    composable<Route.EventDetail> { EventDetailScreen(onBack = nav::navigateUp) }
    composable<Route.TransactionDetail> {
        TransactionDetailScreen(onBack = nav::navigateUp, onOpenEvent = { nav.navigate(Route.EventDetail(it)) })
    }
    composable<Route.ImportPreview> {
        ImportPreviewScreen(
            onDone = nav::closeImportPreview,
        )
    }
    composable<Route.OpeningPositions> { OpeningPositionsScreen(onBack = nav::navigateUp) }
}

private fun NavHostController.openFromSummary(target: SummaryTarget) {
    when (target) {
        is SummaryTarget.Events -> navigateTopLevel(Route.Transactions(term = target.term?.name), restore = false)
        SummaryTarget.Income -> navigateTopLevel(Route.Transactions(showAll = true, type = TransactionType.INCOME.name), restore = false)
        SummaryTarget.Issues -> navigate(Route.Issues)
        SummaryTarget.Import -> navigateTopLevel(Route.ImportExport)
        SummaryTarget.Settings -> navigate(Route.Settings)
    }
}

private fun NavDestination?.isOn(routeClass: KClass<out Route>): Boolean = this?.hierarchy?.any { it.hasRoute(routeClass) } == true
