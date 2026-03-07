package org.vpilo.babymonitor.presentation.approlechoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import babymonitor.presentation.generated.resources.Res
import babymonitor.presentation.generated.resources.app_role_choice_alternative
import babymonitor.presentation.generated.resources.app_role_choice_presentation
import babymonitor.presentation.generated.resources.app_role_monitor
import babymonitor.presentation.generated.resources.app_role_record
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.presentation.MaxUserInterfaceWidth
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun AppRoleChoiceScreen(
    modifier: Modifier = Modifier,
    onRoleChosen: (role: AppRole) -> Unit,
) {
    Surface(
        color = Theme.Colors.mainBackground,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = modifier
                .widthIn(max = MaxUserInterfaceWidth)
                .fillMaxSize()
                .padding(Theme.Paddings.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.app_role_choice_presentation),
                color = Theme.Colors.text,
            )
            Button(
                onClick = { onRoleChosen(AppRole.CAMERA) },
                modifier = Modifier.padding(vertical = Theme.Paddings.Medium),
            ) {
                Text(
                    text = stringResource(Res.string.app_role_record),
                    style = MaterialTheme.typography.titleMedium,
                    color = Theme.Colors.text,
                )
            }

            Text(
                text = stringResource(Res.string.app_role_choice_alternative),
                color = Theme.Colors.text,
            )

            Button(
                onClick = { onRoleChosen(AppRole.MONITOR) },
                modifier = Modifier.padding(vertical = Theme.Paddings.Medium),
            ) {
                Text(
                    text = stringResource(Res.string.app_role_monitor),
                    style = MaterialTheme.typography.titleMedium,
                    color = Theme.Colors.text,
                )
            }
        }
    }
}
