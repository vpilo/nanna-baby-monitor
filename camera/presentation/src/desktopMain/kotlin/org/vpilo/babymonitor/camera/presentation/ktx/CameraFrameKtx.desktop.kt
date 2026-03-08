package org.vpilo.babymonitor.camera.presentation.ktx

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.vpilo.babymonitor.model.CameraFrame

internal actual fun CameraFrame.toImageBitmap(): ImageBitmap =
    image.toComposeImageBitmap()
