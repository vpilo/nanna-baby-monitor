package org.vpilo.babymonitor.model.repository

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.SharedFlow

/**
 * Repository that receives encoded video chunks, decodes them,
 * and exposes decoded frames as a flow of [ImageBitmap].
 */
interface StreamingVideoReceiverRepository {
    val decodedFrames: SharedFlow<ImageBitmap>
}
