package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.vpilo.babymonitor.presentation.AppPreviewTheme

@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = rememberQrCodePainter(data),
        contentDescription = null,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun QrCodeImagePreview() =
    AppPreviewTheme {
        QrCodeImage(data = "bm|1|00000000-0000-0000-0000-000000000000|AB23CD|192.168.1.1")
    }
