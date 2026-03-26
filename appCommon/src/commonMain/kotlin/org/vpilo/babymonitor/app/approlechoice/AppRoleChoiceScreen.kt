package org.vpilo.babymonitor.app.approlechoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_role_choice_alternative
import babymonitor.appcommon.generated.resources.app_role_choice_presentation
import babymonitor.appcommon.generated.resources.app_role_monitor
import babymonitor.appcommon.generated.resources.app_role_record
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun AppRoleChoiceScreen(
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
            color = MaterialTheme.colorScheme.onBackground,
        )
        Button(
            onClick = { onRoleChosen(AppRole.SERVER) },
            modifier = Modifier.padding(vertical = Theme.Paddings.Medium),
        ) {
            Text(
                text = stringResource(Res.string.app_role_record),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = stringResource(Res.string.app_role_choice_alternative),
            color = MaterialTheme.colorScheme.onBackground,
        )

        Button(
            onClick = { onRoleChosen(AppRole.CLIENT) },
            modifier = Modifier.padding(vertical = Theme.Paddings.Medium),
        ) {
            Text(
                text = stringResource(Res.string.app_role_monitor),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
