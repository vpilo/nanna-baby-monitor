package org.vpilo.babymonitor.app.approlechoice

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_icon
import babymonitor.appcommon.generated.resources.app_role_choice_alternative
import babymonitor.appcommon.generated.resources.app_role_choice_icon_description
import babymonitor.appcommon.generated.resources.app_role_choice_presentation_description
import babymonitor.appcommon.generated.resources.app_role_choice_presentation_title
import babymonitor.appcommon.generated.resources.app_role_choice_prompt
import babymonitor.appcommon.generated.resources.app_role_monitor
import babymonitor.appcommon.generated.resources.app_role_record
import babymonitor.appcommon.generated.resources.role_recorder
import babymonitor.appcommon.generated.resources.role_watcher
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.ScrollableBox

@Composable
fun AppRoleChoiceScreen(
    modifier: Modifier = Modifier,
    onRoleChosen: (role: AppRole) -> Unit,
    viewModel: AppRoleChoiceScreenViewModel = koinViewModel(),
) {
    // Keep the VM subscribed.
    viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.effectsFlow) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is AppRoleChoiceScreenEffect.RoleChosen -> onRoleChosen(effect.role)
            }
        }
    }

    AppRoleChoiceScreenContent(
        modifier = modifier.fillMaxSize(),
        onRoleChosen = {
            viewModel.send(AppRoleChoiceScreenAction.RoleChosen(it))
        },
    )
}

@Composable
private fun AppRoleChoiceScreenContent(
    modifier: Modifier = Modifier,
    onRoleChosen: (role: AppRole) -> Unit,
) {
    ScrollableBox(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Theme.Paddings.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
        ) {
            Image(
                modifier = Modifier.size(Theme.Sizes.IconHeader),
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = stringResource(Res.string.app_role_choice_icon_description),
            )
            Text(
                text = stringResource(Res.string.app_role_choice_presentation_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.app_role_choice_presentation_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                modifier = Modifier.padding(top = Theme.Paddings.Medium),
                text = stringResource(Res.string.app_role_choice_prompt),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Button(
                onClick = { onRoleChosen(AppRole.SERVER) },
            ) {
                Image(
                    modifier = Modifier.size(Theme.Sizes.IconLarge),
                    painter = painterResource(Res.drawable.role_recorder),
                    colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onPrimary),
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = Theme.Paddings.Medium),
                    text = stringResource(Res.string.app_role_record),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Text(
                text = stringResource(Res.string.app_role_choice_alternative),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { onRoleChosen(AppRole.CLIENT) },
            ) {
                Image(
                    modifier = Modifier.size(Theme.Sizes.IconLarge),
                    painter = painterResource(Res.drawable.role_watcher),
                    colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onPrimary),
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = Theme.Paddings.Medium),
                    text = stringResource(Res.string.app_role_monitor),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppRoleChoiceScreenPreview() =
    AppPreviewTheme {
        Column {
            AppRoleChoiceScreenContent(
                modifier = Modifier.fillMaxSize(),
                onRoleChosen = {},
            )
        }
    }

@Preview
@Composable
private fun AppRoleChoiceScreenDarkPreview() =
    AppPreviewTheme(useDarkTheme = true) {
        Column {
            AppRoleChoiceScreenContent(
                modifier = Modifier.fillMaxSize(),
                onRoleChosen = {},
            )
        }
    }
