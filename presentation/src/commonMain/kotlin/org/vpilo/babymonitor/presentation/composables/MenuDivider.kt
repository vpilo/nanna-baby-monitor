package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun MenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(Theme.Paddings.Medium))
}

@Preview
@Composable
fun MenuDividerPreview() =
    AppPreviewTheme(useDarkTheme = false) {
        Column {
            Text("Some text")
            MenuDivider()
            Text("Some other text")
        }
    }
