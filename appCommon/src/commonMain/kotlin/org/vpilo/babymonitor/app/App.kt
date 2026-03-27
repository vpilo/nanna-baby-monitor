package org.vpilo.babymonitor.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_connect
import babymonitor.appcommon.generated.resources.app_title_client_home
import babymonitor.appcommon.generated.resources.app_title_permissions
import babymonitor.appcommon.generated.resources.app_title_server_home
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.app.cameraselection.CameraSelectionScreen
import org.vpilo.babymonitor.app.client.ClientHomeScreen
import org.vpilo.babymonitor.app.navigation.NavigationEffect
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.server.ServerHomeScreen
import org.vpilo.babymonitor.camera.presentation.permissioncheck.PermissionCheckScreen
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination

private const val TAG = "App"

@Composable
fun App(viewModel: AppUiFlowViewModel = koinViewModel()) {
    val state = viewModel.stateFlow.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { controller, destination, _ ->
            val route = destination.route
            val backStack = controller.currentBackStack.value.joinToString(" -> ") { it.destination.route.toString() }

            Logger.d(TAG) { "Navigated to: $route" }
            Logger.d(TAG) { "-- Back stack: $backStack" }
        }

        viewModel.effectsFlow.collect { event ->
            when (event) {
                is NavigationEffect.NavigateTo -> navController.navigate(event.route)
            }
        }
    }

    AppTheme {
        MainContainer {
            NavigationRoutes(
                sendAction = { viewModel.send(it) },
                navController = navController,
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun NavigationRoutes(
    sendAction: (AppUiFlowAction) -> Unit,
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Route.RootNavGraph,
    ) {
        navigation<Route.RootNavGraph>(startDestination = Route.AppRoleChooser) {
            composable<Route.AppRoleChooser> {
                AppRoleChoiceScreen(
                    onRoleChosen = { role ->
                        sendAction(AppUiFlowAction.RoleChosen(role))
                    },
                )
            }
            composable<Route.PermissionCheck>(
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally() },
            ) {
                AppDestination(
                    title = Res.string.app_title_permissions,
                    onBackClicked = {
                        navController.navigate(Route.AppRoleChooser) {
                            popUpTo(Route.AppRoleChooser) { inclusive = true }
                        }
                    },
                ) {
                    PermissionCheckScreen(
                        onAllPermissionsGranted = {
                            navController.navigate(Route.ServerHome)
                        },
                    )
                }
            }
            composable<Route.ServerHome>(
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally() },
            ) {
                AppDestination(
                    title = Res.string.app_title_server_home,
                    onBackClicked = {
                        navController.navigate(Route.RootNavGraph) {
                            popUpTo(Route.RootNavGraph) { inclusive = true }
                        }
                    },
                ) {
                    ServerHomeScreen(
                        viewModel = koinViewModel(),
                    )
                }
            }
            composable<Route.ClientConnectionChooser> {
                AppDestination(
                    title = Res.string.app_title_client_connect,
                    onBackClicked = {
                        navController.navigate(Route.AppRoleChooser) {
                            popUpTo(Route.AppRoleChooser) { inclusive = true }
                        }
                    },
                ) {
                    CameraSelectionScreen(
                        viewModel = koinViewModel(),
                        onConnected = {
                            navController.navigate(Route.ClientHome)
                        },
                    )
                }
            }
            composable<Route.ClientHome>(
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally() },
            ) {
                AppDestination(
                    title = Res.string.app_title_client_home,
                    onBackClicked = {
                        navController.navigate(Route.RootNavGraph) {
                            popUpTo(Route.RootNavGraph) { inclusive = true }
                        }
                    },
                ) {
                    ClientHomeScreen(
                        viewModel = koinViewModel(),
                        onDisconnected = {
                            navController.popBackStack()
                        },
                    )
                }
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
                .fillMaxSize()
                .padding(Theme.Paddings.Medium),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        content()
    }
}
