package org.vpilo.babymonitor.presentation.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.vpilo.babymonitor.presentation.AppPreviewTheme

@Composable
fun LoadingIcon(modifier: Modifier = Modifier) = PulseAnimation(modifier)

@Preview
@Composable
private fun LoadingIconBox() =
    AppPreviewTheme {
        LoadingIcon()
    }
