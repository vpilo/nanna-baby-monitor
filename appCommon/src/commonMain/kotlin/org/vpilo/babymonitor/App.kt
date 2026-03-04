package org.vpilo.babymonitor

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.vpilo.babymonitor.navigation.Route
import org.vpilo.babymonitor.presentation.server.ServerPreviewScreenRoot
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.presentation.permissioncheck.PermissionCheckScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = Route.RootNavGraph,
        ) {
            navigation<Route.RootNavGraph>(startDestination = Route.PermissionCheck) {
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
                composable<Route.ServerPreview>(
                    exitTransition = { slideOutHorizontally() },
                    popEnterTransition = { slideInHorizontally() },
                ) {
                    ServerPreviewScreenRoot(
                        viewModel = koinViewModel(),
                    )
                }
            }
        }
    }
}
