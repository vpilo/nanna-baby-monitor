package org.vpilo.babymonitor.camera.presentation.ktx

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.vpilo.babymonitor.model.CameraFrame
import androidx.core.graphics.createBitmap

/**
 * Converts an NV21 [CameraFrame] directly to an [ImageBitmap].
 *
 * NV21 layout: width×height Y bytes, then (width×height/2) interleaved V,U bytes.
 * Falls back to grayscale if the chroma plane is missing.
 */
internal actual fun CameraFrame.toImageBitmap(): ImageBitmap {
    val ySize = width * height
    val hasChroma = bytes.size >= ySize * 3 / 2
    val pixels = IntArray(ySize)

    for (i in 0 until ySize) {
        val y = bytes[i].toInt() and 0xFF

        if (hasChroma) {
            val row = i / width
            val col = i % width

            // VU pair index: each 2×2 block shares one V and one U byte
            val uvIndex = ySize + (row shr 1) * width + (col and 1.inv())
            val v = (bytes[uvIndex].toInt() and 0xFF) - 128
            val u = (bytes[uvIndex + 1].toInt() and 0xFF) - 128

            // ITU-R BT.601 YUV → RGB
            var r = y + (1370 * v shr 10)
            var g = y - (336 * u + 698 * v shr 10)
            var b = y + (1732 * u shr 10)

            r = r.coerceIn(0, 255)
            g = g.coerceIn(0, 255)
            b = b.coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        } else {
            pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
        }
    }

    return createBitmap(width, height)
        .apply { setPixels(pixels, 0, width, 0, 0, width, height) }
        .asImageBitmap()
}
