package org.vpilo.babymonitor.presentation.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ScrollableBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
