package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
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
        Column {
            val fingerprint = "0123456789abcdef".repeat(4)
            val id = "00000000-0000-0000-0000-000000000000"

            Text("Invalid QR")
            QrCodeImage(data = "WIFI:T:nopass;S:SomeOtherQr;;")
            Spacer(modifier = Modifier.height(Theme.Paddings.Large))

            Text("Older version")
            QrCodeImage(data = "bm|1|$fingerprint|$id|DEADBE")
            Spacer(modifier = Modifier.height(Theme.Paddings.Large))

            Text("Newer version")
            QrCodeImage(data = "bm|99|$fingerprint|$id|DEADBE")
            Spacer(modifier = Modifier.height(Theme.Paddings.Large))

            Text("Other device")
            QrCodeImage(data = "bm|2|$fingerprint|$id|DEADBE")
            Spacer(modifier = Modifier.height(Theme.Paddings.Large))
        }
    }
