package org.vpilo.babymonitor.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
            modifier =
                modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            content = content,
        )
    }
}

@Preview
@Composable
fun AppThemeLightThemePreview() {
    AppTheme(useDarkTheme = false) {
        AppDestination(
            title = Res.string.example,
            onBackClicked = {},
        ) {
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

@Preview
@Composable
fun AppThemeDarkThemePreview() {
    AppTheme(useDarkTheme = true) {
        AppDestination(
            title = Res.string.example,
            onBackClicked = {},
        ) {
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
