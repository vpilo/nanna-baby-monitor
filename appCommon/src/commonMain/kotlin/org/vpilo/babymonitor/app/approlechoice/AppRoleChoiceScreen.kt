package org.vpilo.babymonitor.app.approlechoice

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_role_choice_alternative
import babymonitor.appcommon.generated.resources.app_role_choice_presentation
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

@Composable
fun AppRoleChoiceScreen(
    modifier: Modifier = Modifier,
    onRoleChosen: (role: AppRole) -> Unit,
    viewModel: AppRoleChoiceScreenViewModel = koinViewModel(),
) {
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

private val TopLeftTriangle =
    GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(0f, size.height)
        close()
    }

private val BottomRightTriangle =
    GenericShape { size, _ ->
        moveTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }

@Composable
private fun AppRoleChoiceScreenContent(
    modifier: Modifier = Modifier,
    onRoleChosen: (role: AppRole) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.app_role_choice_presentation),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier
                    .padding(Theme.Paddings.Medium),
        )
        Box(modifier = modifier) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(TopLeftTriangle)
                        .clickable { onRoleChosen(AppRole.SERVER) },
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .padding(Theme.Paddings.Large),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text(
                            text = stringResource(Res.string.app_role_record),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier =
                                Modifier
                                    .fillMaxWidth(.75f),
                        )
                        Image(
                            modifier = Modifier.size(Theme.Sizes.MainIcon),
                            painter = painterResource(Res.drawable.role_recorder),
                            contentScale = ContentScale.FillBounds,
                            colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onPrimaryContainer),
                            contentDescription = null,
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(BottomRightTriangle)
                        .clickable { onRoleChosen(AppRole.CLIENT) },
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(Theme.Paddings.Large),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Image(
                            modifier = Modifier.size(Theme.Sizes.MainIcon),
                            painter = painterResource(Res.drawable.role_watcher),
                            contentScale = ContentScale.FillBounds,
                            colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onPrimaryContainer),
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(Res.string.app_role_monitor),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier =
                                Modifier
                                    .fillMaxWidth(.75f),
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                Text(
                    text = stringResource(Res.string.app_role_choice_alternative),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier =
                        Modifier
                            .align(Alignment.Center),
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
                onRoleChosen = {},
            )
        }
    }
