package org.vpilo.babymonitor.camera.presentation

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun decodeToImageBitmap(data: ByteArray): ImageBitmap =
    BitmapFactory.decodeByteArray(data, 0, data.size).asImageBitmap()
