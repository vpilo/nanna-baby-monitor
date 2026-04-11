package org.vpilo.babymonitor.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.presentation.generated.resources.Res
import babymonitor.presentation.generated.resources.example
import org.vpilo.babymonitor.presentation.composables.AppDestination

@Composable
fun AppTheme(
    modifier: Modifier = Modifier,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = modifier,
            content = content,
        )
    }
}

@Preview
@Composable
fun AppThemeLightThemePreview() {
    AppThemePreviewContent(useDarkTheme = false)
}

@Preview
@Composable
fun AppThemeDarkThemePreview() {
    AppThemePreviewContent(useDarkTheme = true)
}

@Composable
private fun AppThemePreviewContent(useDarkTheme: Boolean) {
    AppTheme(useDarkTheme = useDarkTheme) {
        AppDestination(
            title = Res.string.example,
            onMainActionClicked = {},
        ) {
            Column {
                Text("Hello, World!")
                Spacer(modifier = Modifier.size(Theme.Paddings.Medium))
                Button(onClick = {}) {
                    Text(
                        modifier = Modifier.padding(Theme.Paddings.Small),
                        text = "Sample Button",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
