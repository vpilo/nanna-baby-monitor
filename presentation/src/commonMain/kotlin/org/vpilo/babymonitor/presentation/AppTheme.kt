package org.vpilo.babymonitor.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppTheme(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MaterialTheme {
        Surface(
            color = Theme.Colors.mainBackground,
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            content = content,
        )
    }
}
