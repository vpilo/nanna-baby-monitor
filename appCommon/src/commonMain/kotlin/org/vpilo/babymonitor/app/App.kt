package org.vpilo.babymonitor.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.app.cameraselection.CameraSelectionScreen
import org.vpilo.babymonitor.app.client.home.ClientHomeScreen
import org.vpilo.babymonitor.app.menu.MenuScreen
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.onboarding.OnboardingScreen
import org.vpilo.babymonitor.app.permissioncheck.AppPermissionCheckScreen
import org.vpilo.babymonitor.app.server.home.ServerHomeScreen
import org.vpilo.babymonitor.camera.presentation.permissioncheck.CameraPermissionCheckScreen
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.presentation.AppTheme

private const val TAG = "App"

@Composable
fun App() {
    val navController = rememberNavController()
    LaunchedEffect(navController) {
        navController.currentBackStack.collect { backStack ->
            if (backStack.isEmpty()) return@collect
            Logger.d(TAG) {
                fun NavBackStackEntry.name(): String = destination.route?.substringAfterLast('.') ?: "Root"
                "Navigated to: ${backStack.joinToString(" -> ") { it.name() }}"
            }
        }
    }

    AppTheme {
        MainContainer {
            NavHost(
                navController = navController,
                startDestination = Route.RootNavGraph,
            ) {
                navigation<Route.RootNavGraph>(startDestination = Route.Onboarding) {
                    navigationRoutes(
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateTo = { route, popUpToRoute ->
                            navController.navigate(route) {
                                popUpToRoute?.let { popUpTo(popUpToRoute) { inclusive = false } }
                            }
                        },
                        onNavigateToRoot = {
                            navController.navigate(Route.AppPermissionCheck) {
                                popUpTo<Route.RootNavGraph> { inclusive = false }
                                launchSingleTop = true
                            }
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
                .systemBarsPadding()
                .fillMaxSize(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        content()
    }
}

@Suppress("LongMethod")
private fun NavGraphBuilder.navigationRoutes(
    onNavigateTo: (route: Route, popUpTo: Route?) -> Unit = { _, _ -> },
    onNavigateUp: () -> Unit = {},
    onNavigateToRoot: () -> Unit = {},
) {
    composable<Route.Onboarding> {
        OnboardingScreen(
            onSavedRole = { role ->
                when (role) {
                    AppRole.SERVER -> {
                        onNavigateTo(Route.CameraPermissionCheck, Route.Onboarding)
                    }

                    AppRole.CLIENT -> {
                        onNavigateTo(Route.CameraSelection, Route.Onboarding)
                    }

                    AppRole.UNDECIDED -> {
                        onNavigateToRoot()
                    }
                }
            },
        )
    }

    composable<Route.AppRoleChooser> {
        AppRoleChoiceScreen(
            onRoleChosen = { role ->
                Logger.d(TAG) { "Role chosen: $role" }
                onNavigateTo(
                    when (role) {
                        AppRole.SERVER -> Route.CameraPermissionCheck
                        AppRole.CLIENT -> Route.CameraSelection
                        AppRole.UNDECIDED -> error("UNDECIDED role should not be selectable")
                    },
                    null,
                )
            },
        )
    }

    composable<Route.Menu> {
        MenuScreen(
            viewModel = koinViewModel(),
            onBackClicked = onNavigateUp,
            onNavigateTo = onNavigateTo,
            onNavigateToRoot = onNavigateToRoot,
        )
    }

    composable<Route.Quit> {
        val quitApplication = LocalQuitApplication.current
        LaunchedEffect(Unit) {
            quitApplication()
        }
    }

    composable<Route.AppPermissionCheck> {
        AppPermissionCheckScreen(
            onAllPermissionsGranted = {
                onNavigateTo(Route.AppRoleChooser, Route.AppPermissionCheck)
            },
        )
    }

    composable<Route.CameraPermissionCheck> {
        CameraPermissionCheckScreen(
            onAllPermissionsGranted = {
                onNavigateTo(Route.ServerHome, null)
            },
            onBackClicked = onNavigateToRoot,
        )
    }

    composable<Route.ServerHome> {
        ServerHomeScreen(
            viewModel = koinViewModel(),
            onMenuClicked = {
                onNavigateTo(Route.Menu, null)
            },
        )
    }

    composable<Route.CameraSelection> {
        CameraSelectionScreen(
            viewModel = koinViewModel(),
            onConnected = {
                onNavigateTo(Route.ClientHome, null)
            },
            onMenuClicked = {
                onNavigateTo(Route.Menu, null)
            },
        )
    }

    composable<Route.ClientHome> {
        ClientHomeScreen(
            viewModel = koinViewModel(),
            onDisconnected = {
                onNavigateTo(Route.CameraSelection, Route.CameraSelection)
            },
            onMenuClicked = {
                onNavigateTo(Route.Menu, null)
            },
        )
    }
}
