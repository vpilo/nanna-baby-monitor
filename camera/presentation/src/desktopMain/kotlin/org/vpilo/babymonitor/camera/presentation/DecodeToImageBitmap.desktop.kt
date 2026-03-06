package org.vpilo.babymonitor.camera.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

internal actual fun decodeToImageBitmap(data: ByteArray): ImageBitmap =
    Image.makeFromEncoded(data).toComposeImageBitmap()
