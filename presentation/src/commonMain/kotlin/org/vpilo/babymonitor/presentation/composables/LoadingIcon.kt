package org.vpilo.babymonitor.presentation.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun LoadingIcon(modifier: Modifier = Modifier) =
    PulseAnimation(modifier)

@Preview
@Composable
private fun LoadingIconBox() {
    MaterialTheme {
        LoadingIcon()
    }
}
