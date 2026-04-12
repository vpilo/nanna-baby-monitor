package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.preview.makePlaceholderCameraFrame

@Composable
fun Backdrop(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.scrim,
    borderColor: Color = MaterialTheme.colorScheme.inverseSurface,
    padding: Dp = Theme.Paddings.Small,
    border: Dp = Theme.Borders.Thin,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .drawBehind {
                    val radius = CornerRadius(padding.toPx())
                    drawRoundRect(
                        color = color,
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = radius,
                        style = Stroke(width = border.toPx()),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(padding),
        ) {
            content()
        }
    }
}

@Preview
@Composable
fun BackdropPreview() =
    AppPreviewTheme(useDarkTheme = false) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = makePlaceholderCameraFrame(isDarkMode = false),
                contentDescription = null,
            )
            Backdrop {
                Text("This is a backdrop!")
            }
        }
    }

@Preview
@Composable
fun BackdropDarkPreview() =
    AppPreviewTheme(useDarkTheme = true) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = makePlaceholderCameraFrame(isDarkMode = true),
                contentDescription = null,
            )
            Backdrop {
                Text("This is a backdrop!")
            }
        }
    }
