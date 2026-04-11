package org.vpilo.babymonitor.app.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.NavHostController
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_copyright
import babymonitor.appcommon.generated.resources.app_name
import babymonitor.appcommon.generated.resources.app_title_menu
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.settings.presentation.composables.MenuItem

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier,
    viewModel: MenuScreenViewModel,
    navController: NavHostController,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    AppDestination(
        title = Res.string.app_title_menu,
        mainAction = AppDestinationMainAction.Back,
        onMainActionClicked = onBackClicked,
    ) {
        MenuScreenContent(
            modifier = modifier,
            menuItems = { AppMenuContents(currentRole = state.currentRole, onNavigateTo = { navController.navigate(it) }) },
        )
    }
}

@Composable
private fun MenuScreenContent(
    modifier: Modifier = Modifier,
    menuItems: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        Column(
            modifier =
                modifier
                    .padding(top = Theme.Paddings.Small)
                    .weight(.9f),
            horizontalAlignment = Alignment.Start,
        ) {
            menuItems()
        }
        Column(
            modifier =
                modifier
                    .padding(bottom = Theme.Paddings.Small)
                    .weight(.1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.surfaceDim,
            )
            Text(
                text = stringResource(Res.string.app_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.surfaceDim,
            )
        }
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
