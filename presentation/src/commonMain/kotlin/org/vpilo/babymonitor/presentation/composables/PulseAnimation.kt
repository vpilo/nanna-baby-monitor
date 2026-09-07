package org.vpilo.babymonitor.presentation.composables

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun PulseAnimation(
    modifier: Modifier = Modifier,
    color: Color,
) {
    val transition = rememberInfiniteTransition()
    val inPreviewMode = LocalInspectionMode.current
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (inPreviewMode) .5f else 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1_000),
                repeatMode = RepeatMode.Restart,
            ),
    )

    Box(
        modifier =
            modifier
                .size(Theme.Sizes.Button)
                .graphicsLayer {
                    scaleX = progress
                    scaleY = progress
                    alpha = 1f - progress
                }.border(
                    width = Theme.Sizes.Button,
                    color = color,
                    shape = CircleShape,
                ),
    )
}
