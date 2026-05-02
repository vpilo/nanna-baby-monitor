package org.vpilo.babymonitor.app.navigation.ktx

import androidx.navigation.NavHostController
import org.vpilo.babymonitor.app.navigation.Route

/**
 * Navigates to [Route.AppRoleChooser], clearing the back stack.
 * Ensures any previously active screens depending on app role leave the stack.
 */
fun NavHostController.navigateToAppRoleChooser() {
    navigate(Route.AppRoleChooser) {
        popUpTo<Route.RootNavGraph> { inclusive = false }
        launchSingleTop = true
    }
}
