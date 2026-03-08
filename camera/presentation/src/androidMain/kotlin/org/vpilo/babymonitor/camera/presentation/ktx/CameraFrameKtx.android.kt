package org.vpilo.babymonitor.camera.presentation.ktx

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import org.vpilo.babymonitor.model.CameraFrame
import java.nio.ByteBuffer

internal actual fun CameraFrame.toImageBitmap(): ImageBitmap =
    createBitmap(width, height) // Defaults to ARGB_8888
        .apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        }.asImageBitmap()
