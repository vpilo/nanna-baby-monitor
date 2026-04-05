package org.vpilo.babymonitor.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.app.cameraselection.CameraSelectionScreen
import org.vpilo.babymonitor.app.client.ClientHomeScreen
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.onboarding.OnboardingScreen
import org.vpilo.babymonitor.app.server.ServerHomeScreen
import org.vpilo.babymonitor.camera.presentation.permissioncheck.PermissionCheckScreen
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme

private const val TAG = "App"

@Composable
fun App() {
    val navController = rememberNavController()
    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { controller, destination, _ ->
            val route = destination.route
            val backStack = controller.currentBackStack.value.joinToString(" -> ") { it.destination.route.toString() }

            Logger.d(TAG) { "Navigated to: $route" }
            Logger.d(TAG) { "-- Back stack: $backStack" }
        }
    }

    AppTheme {
        MainContainer {
            NavigationRoutes(navController = navController)
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun NavigationRoutes(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Route.RootNavGraph,
    ) {
        navigation<Route.RootNavGraph>(startDestination = Route.Onboarding) {
            composable<Route.Onboarding> {
                OnboardingScreen(
                    onSavedRole = { role ->
                        when (role) {
                            AppRole.SERVER ->
                                navController.navigate(Route.PermissionCheck) {
                                    popUpTo(Route.Onboarding) { inclusive = true }
                                }

                            AppRole.CLIENT ->
                                navController.navigate(Route.CameraSelection) {
                                    popUpTo(Route.Onboarding) { inclusive = true }
                                }

                            AppRole.UNDECIDED ->
                                navController.navigate(Route.AppRoleChooser) {
                                    popUpTo(Route.Onboarding) { inclusive = true }
                                }
                        }

                    },
                )
            }

            composable<Route.AppRoleChooser> {
                AppRoleChoiceScreen(
                    onRoleChosen = { role ->
                        Logger.d(TAG) { "Role chosen: $role" }
                        navController.navigate(
                            when (role) {
                                AppRole.SERVER -> Route.PermissionCheck
                                AppRole.CLIENT -> Route.CameraSelection
                                AppRole.UNDECIDED -> error("UNDECIDED role should not be selectable")
                            },
                        )
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
                    onBackClicked = {
                        navController.navigate(Route.AppRoleChooser) {
                            popUpTo(Route.AppRoleChooser) { inclusive = true }
                        }
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
                        navController.navigate(Route.AppRoleChooser) {
                            popUpTo(Route.RootNavGraph) { inclusive = true }
                        }
                    },
                )
            }
            composable<Route.CameraSelection> {
                CameraSelectionScreen(
                    viewModel = koinViewModel(),
                    onConnected = {
                        navController.navigate(Route.ClientHome)
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
                    onDisconnected = {
                        navController.popBackStack()
                    },
                    onBackClicked = {
                        navController.navigate(Route.RootNavGraph) {
                            popUpTo(Route.RootNavGraph) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MainContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = Theme.Sizes.UserInterfaceMaxWidth)
                .statusBarsPadding()
                .fillMaxSize(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        content()
    }
}
