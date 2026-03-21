package org.vpilo.babymonitor.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.app.client.ClientHomeScreen
import org.vpilo.babymonitor.app.clientconnectionchooser.ClientConnectionChooserScreen
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.server.ServerHomeScreen
import org.vpilo.babymonitor.camera.presentation.permissioncheck.PermissionCheckScreen
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.presentation.AppTheme

@Composable
fun App(
    viewModel: AppUiFlowViewModel = koinViewModel(),
) {
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
                            viewModel.onAction(AppUiFlowAction.RoleChosen(role))
                            when (role) {
                                AppRole.SERVER ->
                                    navController.navigate(Route.PermissionCheck)

                                AppRole.CLIENT ->
                                    navController.navigate(Route.ClientConnectionChooser)

                                AppRole.UNDECIDED -> error("UNDECIDED role should not be selectable")
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
                composable<Route.ClientConnectionChooser> {
                    ClientConnectionChooserScreen(
                        viewModel = koinViewModel(),
                        onConnected = {
                            context.launch {
                                navController.navigate(Route.ClientHome) {
                                    popUpTo(Route.ClientConnectionChooser) { inclusive = true }
                                }
                            }
                        },
                        onBackClicked = {
                            context.launch {
                                navController.navigate(Route.AppRoleChooser) {
                                    popUpTo(Route.AppRoleChooser) {
                                        inclusive = true
                                    }
                                }
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
                            context.launch {
                                navController.navigate(Route.RootNavGraph) {
                                    popUpTo(Route.RootNavGraph) {
                                        inclusive = true
                                    }
                                }
                            }
                        },
                        onDisconnected = {
                            context.launch {
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
    }
}
