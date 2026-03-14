package org.vpilo.babymonitor.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.app.client.ClientPreviewScreenRoot
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.server.ServerPreviewScreenRoot
import org.vpilo.babymonitor.camera.presentation.permissioncheck.PermissionCheckScreen
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.network.client.createNetworkClient
import org.vpilo.babymonitor.network.server.createNetworkServer
import org.vpilo.babymonitor.presentation.AppTheme

// Temporary role assignment at startup, until onboarding is implemented.
var CURRENT_APP_ROLE: AppRole = AppRole.CAMERA
    private set

@Composable
fun App() {
    AppTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = Route.RootNavGraph,
        ) {
            navigation<Route.RootNavGraph>(startDestination = Route.AppRoleChooser) {
                composable<Route.AppRoleChooser> {
                    AppRoleChoiceScreen(
                        onRoleChosen = { role ->
                            CURRENT_APP_ROLE = role

                            if (CURRENT_APP_ROLE == AppRole.CAMERA) {
                                navController.navigate(Route.PermissionCheck)
                            } else {
                                navController.navigate(Route.ClientPreview)
                            }
                        },
                    )
                }
                composable<Route.PermissionCheck>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    PermissionCheckScreen(
                        onAllPermissionsGranted = {
                            navController.navigate(Route.ServerPreview)
                        },
                    )
                }
                composable<Route.ClientPreview>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    LaunchedEffect(Unit) {
                        createNetworkClient()
                    }
                    ClientPreviewScreenRoot()
                }
                composable<Route.ServerPreview>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    LaunchedEffect(Unit) {
                        createNetworkServer()
                    }
                    ServerPreviewScreenRoot()
                }
            }
        }
    }
}
