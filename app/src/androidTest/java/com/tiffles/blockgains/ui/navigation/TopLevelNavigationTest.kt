package com.tiffles.blockgains.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [navigateTopLevel] against a minimal graph with the app's real routes and start destination.
 * Guards the bug where tapping Summary did nothing (or showed another tab) after Transactions
 * had been opened from a Summary card, because the start destination restored the other tab.
 */
class TopLevelNavigationTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private val tabs: List<Pair<Route, String>> = listOf(
        Route.Summary to "summary screen",
        Route.Transactions() to "transactions screen",
        Route.ImportExport to "import screen",
    )

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Route.Summary) {
                composable<Route.Summary> { Text("summary screen") }
                composable<Route.Transactions> { Text("transactions screen") }
                composable<Route.ImportExport> { Text("import screen") }
                composable<Route.ImportPreview> { Text("preview screen") }
            }
        }
    }

    @Test
    fun everyTabReachesEveryTab() {
        for ((from, _) in tabs) {
            for ((to, label) in tabs) {
                go(from)
                go(to)
                assertOn(to::class, label)
            }
        }
    }

    @Test
    fun summaryWorksAfterTransactionsOpenedFromSummaryCard() {
        go(Route.Transactions(term = "LONG_TERM"), restore = false)
        go(Route.Summary)
        assertOn(Route.Summary::class, "summary screen")

        go(Route.Transactions(showAll = true, type = "INCOME"), restore = false)
        go(Route.ImportExport)
        go(Route.Summary)
        assertOn(Route.Summary::class, "summary screen")
    }

    @Test
    fun summaryLeavesOnlyItselfOnTheBackStack() {
        go(Route.Transactions(term = "LONG_TERM"), restore = false)
        go(Route.ImportExport)
        go(Route.Summary)
        val routes = compose.runOnUiThread { nav.currentBackStack.value.mapNotNull { it.destination.route } }
        assertThat(routes.filterNot { it == nav.graph.route }).hasSize(1)
    }

    @Test
    fun otherTabsStillRestoreTheirState() {
        go(Route.Transactions(term = "LONG_TERM"), restore = false)
        go(Route.Summary)
        go(Route.Transactions())
        val route = compose.runOnUiThread { nav.currentBackStackEntry!!.toRoute<Route.Transactions>() }
        assertThat(route.term).isEqualTo("LONG_TERM")
    }

    @Test
    fun closingImportPreviewReturnsToImportExport() {
        go(Route.Transactions(term = "LONG_TERM"), restore = false)
        go(Route.ImportExport)
        openPreview()
        // The preview's onDone in BlockGainsAppRoot (back, Cancel and after Confirm).
        compose.runOnUiThread { nav.closeImportPreview() }
        compose.waitForIdle()
        assertOn(Route.ImportExport::class, "import screen")
    }

    @Test
    fun closingImportPreviewTwice_staysOnImportExport() {
        go(Route.ImportExport)
        openPreview()
        compose.runOnUiThread {
            nav.closeImportPreview()
            nav.closeImportPreview()
        }
        compose.waitForIdle()
        assertOn(Route.ImportExport::class, "import screen")
    }

    @Test
    fun afterClosingImportPreview_summaryTabStillWorks_andImportTabDoesNotRestoreThePreview() {
        go(Route.ImportExport)
        openPreview()
        compose.runOnUiThread { nav.closeImportPreview() }
        go(Route.Summary)
        assertOn(Route.Summary::class, "summary screen")
        go(Route.ImportExport)
        assertOn(Route.ImportExport::class, "import screen")
    }

    private fun openPreview() {
        compose.runOnUiThread { nav.navigate(Route.ImportPreview) }
        compose.waitForIdle()
        assertOn(Route.ImportPreview::class, "preview screen")
    }

    private fun go(route: Route, restore: Boolean = true) {
        compose.runOnUiThread { nav.navigateTopLevel(route, restore) }
        compose.waitForIdle()
    }

    private fun assertOn(routeClass: KClass<out Route>, label: String) {
        val onRoute = compose.runOnUiThread { nav.currentDestination?.hasRoute(routeClass) == true }
        assertThat(onRoute).isTrue()
        compose.onNodeWithText(label).assertIsDisplayed()
    }
}
