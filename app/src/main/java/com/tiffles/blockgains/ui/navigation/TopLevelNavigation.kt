package com.tiffles.blockgains.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Switches bottom-nav destinations without stacking them. [restore] brings back the tab's
 * previous state; off when the caller passes new filter arguments.
 *
 * The start destination (Summary) is never restored: a non-inclusive `popUpTo(start)` with
 * `saveState` also files the popped tab's back stack under the start destination's id, so
 * restoring Summary would bring that tab straight back and the Summary tap would do nothing.
 */
fun NavHostController.navigateTopLevel(route: Route, restore: Boolean = true) {
    val start = graph.findStartDestination()
    navigate(route) {
        popUpTo(start.id) { saveState = restore }
        launchSingleTop = true
        restoreState = restore && !start.hasRoute(route::class)
    }
}

/**
 * Leaves the import preview (back, Cancel or after Confirm) for the screen that opened it,
 * Import / Export, without switching tabs. Its picked file stays selected. Does nothing if the
 * preview is already gone, so a second signal can't pop Import / Export too.
 */
fun NavHostController.closeImportPreview() {
    if (currentDestination?.hasRoute(Route.ImportPreview::class) == true) popBackStack()
}
