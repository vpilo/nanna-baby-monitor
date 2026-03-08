package org.vpilo.babymonitor.camera.presentation.ktx

import androidx.compose.ui.graphics.ImageBitmap
import org.vpilo.babymonitor.model.CameraFrame

internal expect fun CameraFrame.toImageBitmap(): ImageBitmap
