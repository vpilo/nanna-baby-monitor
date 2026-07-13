package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.github.alexzhirkevich.qrose.options.QrColors
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
) {
    Image(
        modifier =
            modifier
                .background(Color.White)
                .padding(Theme.Paddings.Medium),
        painter =
            rememberQrCodePainter(
                data = data,
            ),
        contentDescription = null,
    )
}

@Preview
@Composable
private fun QrCodeImagePreview() =
    AppPreviewTheme {
        QrCodeImage(data = "bm|1|00000000-0000-0000-0000-000000000000|AB23CD|192.168.1.1")
    }
