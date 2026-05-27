package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.vpilo.babymonitor.model.OpaqueVideoStream

@Composable
internal expect fun PanningVideoFeedContent(
    modifier: Modifier = Modifier.Companion,
    videoStream: OpaqueVideoStream,
    originalFrameSize: IntSize,
    containerSize: IntSize,
    maxPanningAllowed: Offset,
    cropScale: Float,
    panOffset: Offset,
    rotation: Int,
)
