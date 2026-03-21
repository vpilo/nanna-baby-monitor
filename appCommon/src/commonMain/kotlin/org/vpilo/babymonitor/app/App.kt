package org.vpilo.babymonitor.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.app.client.ClientHomeScreenRoot
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.server.ServerViewScreenRoot
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
        val context = rememberCoroutineScope()
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
                        createNetworkClient(
                            onDisconnect = {
                                context.launch {
                                    navController.navigate(Route.RootNavGraph) {
                                        popUpTo(Route.RootNavGraph) {
                                            inclusive = true
                                        }
                                    }
                                }
                            },
                        )
                    }
                    ClientHomeScreenRoot(
                        viewModel = koinViewModel(),
                    )
                }
                composable<Route.ServerPreview>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    LaunchedEffect(Unit) {
                        createNetworkServer()
                    }
                    ServerViewScreenRoot()
                }
            }
        }
    }
}
