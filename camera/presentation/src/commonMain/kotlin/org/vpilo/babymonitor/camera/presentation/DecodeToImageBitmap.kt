package org.vpilo.babymonitor.camera.presentation

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun decodeToImageBitmap(data: ByteArray): ImageBitmap
