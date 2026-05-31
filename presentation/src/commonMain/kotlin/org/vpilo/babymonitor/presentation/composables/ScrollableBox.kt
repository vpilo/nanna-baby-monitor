package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.vpilo.babymonitor.presentation.AppTheme

@Composable
expect fun ScrollableBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Preview(widthDp = 600, heightDp = 400)
@Composable
private fun ScrollableBoxPreview() {
    AppTheme {
        ScrollableBox(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                repeat(60) { index ->
                    Text(text = "Item ${index + 1}")
                }
            }
        }
    }
}
