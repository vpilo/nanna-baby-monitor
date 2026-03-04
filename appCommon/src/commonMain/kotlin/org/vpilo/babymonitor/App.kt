package org.vpilo.babymonitor

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.navigation.Route
import org.vpilo.babymonitor.network.client.createNetworkClient
import org.vpilo.babymonitor.network.server.createNetworkServer
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.approlechoice.AppRoleChoiceScreen
import org.vpilo.babymonitor.presentation.client.ClientPreviewScreenRoot
import org.vpilo.babymonitor.presentation.permissioncheck.PermissionCheckScreen
import org.vpilo.babymonitor.presentation.server.ServerPreviewScreenRoot

// Temporary role assignment at startup, until onboarding is implemented.
var CURRENT_APP_ROLE: AppRole = AppRole.CAMERA
    private set

@Composable
@Preview
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

@Composable
private fun AppTheme(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MaterialTheme {
        Surface(
            color = Theme.Colors.mainBackground,
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            content = content,
        )
    }
}
