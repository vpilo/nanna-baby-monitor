package org.vpilo.babymonitor.camera.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap

internal actual fun rgbaToImageBitmap(data: ByteArray, width: Int, height: Int): ImageBitmap {
    val bitmap = createBitmap(width, height) // Defaults to ARGB_8888
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data))
    return bitmap.asImageBitmap()
}
