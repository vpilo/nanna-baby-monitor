package org.vpilo.babymonitor.app.navigation.ktx

import androidx.navigation.NavHostController
import org.vpilo.babymonitor.app.navigation.Route

/**
 * Navigates to [Route.AppPermissionCheck], clearing the back stack.
 * Ensures any previously active screens depending on app role leave the stack.
 * Used as the gate before [Route.AppRoleChooser] so global app permissions are requested first.
 */
fun NavHostController.navigateToAppPermissionCheck() {
    navigate(Route.AppPermissionCheck) {
        popUpTo<Route.RootNavGraph> { inclusive = false }
        launchSingleTop = true
    }
}
