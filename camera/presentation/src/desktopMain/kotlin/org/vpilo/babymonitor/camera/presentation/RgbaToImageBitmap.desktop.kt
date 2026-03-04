package org.vpilo.babymonitor.camera.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun rgbaToImageBitmap(data: ByteArray, width: Int, height: Int): ImageBitmap {
    val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val image = Image.makeRaster(imageInfo, data, width * 4)
    return image.toComposeImageBitmap()
}
