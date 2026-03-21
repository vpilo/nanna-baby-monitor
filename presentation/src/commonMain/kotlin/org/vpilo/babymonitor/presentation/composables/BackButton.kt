package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import babymonitor.presentation.generated.resources.Res
import babymonitor.presentation.generated.resources.back
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.Theme

private val buttonBackgroundBrush = Brush.radialGradient(listOf(Theme.Colors.accent, Color.Transparent), radius = 15f)

@Composable
fun BackButton(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .fillMaxWidth(),
    ) {
        IconButton(
            onClick = onBackClicked,
            modifier = Modifier
                .padding(Theme.Paddings.Small)
                .background(buttonBackgroundBrush),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = stringResource(Res.string.back),
            )
        }
    }
    Spacer(modifier = Modifier.height(Theme.Paddings.Medium))
}
