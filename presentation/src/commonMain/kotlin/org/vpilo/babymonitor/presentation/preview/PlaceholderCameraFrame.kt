package org.vpilo.babymonitor.presentation.preview

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

val placeholderFrame: ImageBitmap
    get() {
        val image = ImageBitmap(800, 600)
        val size = Size(image.width.toFloat(), image.height.toFloat())
        val brush = Brush.radialGradient(listOf(Color.Red, Color.Green, Color.Blue), tileMode = TileMode.Mirror)
        val canvas = Canvas(image = image)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
            drawRect(brush = brush)
        }
        return image
    }
