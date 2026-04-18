package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.resources.Res
import org.vpilo.babymonitor.presentation.resources.back

@Composable
fun BackButton(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonBackgroundBrush =
        Brush.radialGradient(
            listOf(MaterialTheme.colorScheme.secondary, Color.Transparent),
            radius = 15f,
        )
    Box(
        contentAlignment = Alignment.TopStart,
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        IconButton(
            onClick = onBackClicked,
            modifier =
                Modifier
                    .padding(Theme.Paddings.Small)
                    .background(buttonBackgroundBrush),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                tint = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
    Spacer(modifier = Modifier.height(Theme.Paddings.Medium))
}
