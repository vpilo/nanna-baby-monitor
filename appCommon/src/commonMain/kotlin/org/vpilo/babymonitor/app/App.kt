package org.vpilo.babymonitor.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.app.client.ClientHomeScreen
import org.vpilo.babymonitor.app.clientconnectionchooser.ClientConnectionChooserScreen
import org.vpilo.babymonitor.app.navigation.NavigationEvent
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.server.ServerHomeScreen
import org.vpilo.babymonitor.camera.presentation.permissioncheck.PermissionCheckScreen
import org.vpilo.babymonitor.presentation.AppTheme

@Composable
fun App(
    viewModel: AppUiFlowViewModel = koinViewModel(),
) {
    AppTheme {
        val navController = rememberNavController()

        LaunchedEffect(Unit) {
            viewModel.navigationEvents.collect { event ->
                when (event) {
                    is NavigationEvent.NavigateTo -> navController.navigate(event.route)
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = Route.RootNavGraph,
        ) {
            navigation<Route.RootNavGraph>(startDestination = Route.AppRoleChooser) {
                composable<Route.AppRoleChooser> {
                    AppRoleChoiceScreen(
                        onRoleChosen = { role ->
                            viewModel.onAction(AppUiFlowAction.RoleChosen(role))
                        },
                    )
                }
                composable<Route.PermissionCheck>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    PermissionCheckScreen(
                        onAllPermissionsGranted = {
                            navController.navigate(Route.ServerHome)
                        },
                    )
                }
                composable<Route.ServerHome>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    ServerHomeScreen(
                        viewModel = koinViewModel(),
                        onBackClicked = {
                            navController.navigate(Route.RootNavGraph) {
                                popUpTo(Route.RootNavGraph) { inclusive = true }
                            }
                        },
                    )
                }
                composable<Route.ClientConnectionChooser> {
                    ClientConnectionChooserScreen(
                        viewModel = koinViewModel(),
                        onConnected = {
                            navController.navigate(Route.ClientHome) {
                                popUpTo(Route.ClientConnectionChooser) { inclusive = true }
                            }
                        },
                        onBackClicked = {
                            navController.navigate(Route.AppRoleChooser) {
                                popUpTo(Route.AppRoleChooser) { inclusive = true }
                            }
                        },
                    )
                }
                composable<Route.ClientHome>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    ClientHomeScreen(
                        viewModel = koinViewModel(),
                        onBackClicked = {
                            navController.navigate(Route.RootNavGraph) {
                                popUpTo(Route.RootNavGraph) { inclusive = true }
                            }
                        },
                        onDisconnected = {
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
