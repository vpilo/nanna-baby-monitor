package org.vpilo.babymonitor.app.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_copyright
import babymonitor.appcommon.generated.resources.app_name
import babymonitor.appcommon.generated.resources.app_title_menu
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.presentation.composables.ScrollableBox
import org.vpilo.babymonitor.settings.presentation.composables.MenuItem

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier,
    viewModel: MenuScreenViewModel,
    onBackClicked: () -> Unit = {},
    onNavigateTo: (route: Route, popUpTo: Route?) -> Unit = { _, _ -> },
    onNavigateToRoot: () -> Unit = {},
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    AppDestination(
        modifier = modifier,
        title = Res.string.app_title_menu,
        mainAction = AppDestinationMainAction.Back,
        onMainActionClicked = onBackClicked,
    ) {
        MenuScreenContent(
            menuItems = {
                AppMenuContents(
                    currentRole = state.currentRole,
                    showDisconnect = state.isConnected,
                    onNavigateToRoot = onNavigateToRoot,
                    onNavigateTo = onNavigateTo,
                )
            },
        )
    }
}

@Composable
private fun MenuScreenContent(
    modifier: Modifier = Modifier,
    menuItems: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScrollableBox(modifier = Modifier.weight(1f)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Theme.Paddings.Small),
                horizontalAlignment = Alignment.Start,
            ) {
                menuItems()
            }
        }
        Text(
            modifier = Modifier.padding(top = Theme.Paddings.Medium),
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = stringResource(Res.string.app_copyright),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Preview
@Composable
private fun MenuScreenPreview() =
    AppPreviewTheme {
        MenuScreenContent {
            MenuItem(imageVector = Icons.AutoMirrored.Filled.ExitToApp, title = "Quit", onClick = {})
        }
    }
