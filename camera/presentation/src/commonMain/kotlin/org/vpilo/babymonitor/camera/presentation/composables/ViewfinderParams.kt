package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** Crop-fill scale and the resulting allowed pan range for the viewfinder. */
internal data class ViewfinderParams(
    val cropScale: Float,
    val maxPan: Offset,
) {
    companion object {
        /**
         * Computes the crop-fill scale and pan range for the viewfinder. The frame is drawn at its true
         * aspect, uniformly scaled to fill the container, with overflow on the longer axis clipped and
         * available as pan range (half the overflow on each side). Frame dimensions swap under 90°/270°
         * rotation.
         */
        fun compute(
            containerSize: IntSize,
            frameSize: IntSize,
            rotation: Int,
        ): ViewfinderParams {
            if (containerSize == IntSize.Zero || frameSize == IntSize.Zero) {
                return ViewfinderParams(cropScale = 1f, maxPan = Offset.Zero)
            }

            val rotated = rotation == 90 || rotation == 270
            val width = if (rotated) frameSize.height else frameSize.width
            val height = if (rotated) frameSize.width else frameSize.height

            val cropScale =
                maxOf(
                    containerSize.width.toFloat() / width.toFloat(),
                    containerSize.height.toFloat() / height.toFloat(),
                )
            val maxPan =
                Offset(
                    x = ((cropScale * width - containerSize.width) / 2f).coerceAtLeast(0f),
                    y = ((cropScale * height - containerSize.height) / 2f).coerceAtLeast(0f),
                )
            return ViewfinderParams(cropScale, maxPan)
        }
    }
}
