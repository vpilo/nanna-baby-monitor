package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.resources.Res
import org.vpilo.babymonitor.presentation.resources.back
import org.vpilo.babymonitor.presentation.resources.example
import org.vpilo.babymonitor.presentation.resources.menu

@Composable
fun AppDestination(
    modifier: Modifier = Modifier,
    title: StringResource,
    mainAction: AppDestinationMainAction = AppDestinationMainAction.Back,
    onMainActionClicked: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            modifier = modifier,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            title = {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            navigationIcon = {
                IconButton(onClick = onMainActionClicked) {
                    when (mainAction) {
                        AppDestinationMainAction.None -> {
                            return@IconButton
                        }

                        AppDestinationMainAction.Back -> {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back),
                            )
                        }

                        AppDestinationMainAction.Menu -> {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(Res.string.menu),
                            )
                        }
                    }
                }
            },
            actions = actions,
        )
        Box(
            modifier =
                modifier
                    .fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun AppDestinationPreview() =
    AppPreviewTheme {
        AppDestination(
            title = Res.string.example,
            onMainActionClicked = {},
        ) {
            Text(
                text = "Example content",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

@Preview
@Composable
private fun AppDestinationMenuPreview() =
    AppPreviewTheme {
        AppDestination(
            title = Res.string.example,
            mainAction = AppDestinationMainAction.Menu,
            onMainActionClicked = {},
        ) {
            Text(
                text = "Example content",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
