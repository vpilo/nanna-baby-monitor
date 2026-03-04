package org.vpilo.babymonitor.camera.presentation

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun rgbaToImageBitmap(data: ByteArray, width: Int, height: Int): ImageBitmap
