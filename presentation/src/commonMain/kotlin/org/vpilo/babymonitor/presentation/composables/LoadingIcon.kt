package org.vpilo.babymonitor.presentation.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.vpilo.babymonitor.presentation.AppPreviewTheme

@Composable
fun LoadingIcon(modifier: Modifier = Modifier) =
    PulseAnimation(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondary,
    )

@Preview
@Composable
private fun LoadingIconPreview() =
    AppPreviewTheme {
        LoadingIcon()
    }
