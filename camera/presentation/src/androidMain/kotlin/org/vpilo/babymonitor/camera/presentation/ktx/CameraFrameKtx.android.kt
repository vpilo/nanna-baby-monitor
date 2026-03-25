package org.vpilo.babymonitor.camera.presentation.ktx

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.vpilo.babymonitor.model.CameraFrame
import java.io.ByteArrayOutputStream

/**
 * Converts a tightly-packed NV12 [CameraFrame] to an [ImageBitmap]
 * using Android's native [YuvImage] + [BitmapFactory] path.
 *
 * NV12 layout (as produced by VideoCaptureDataSource):
 *   width×height Y bytes, then width×height/2 interleaved U,V bytes.
 *
 * [YuvImage] expects NV21 (V,U order), so the UV pairs are swapped
 * in-place before compression. This swap + JPEG round-trip is still
 * significantly faster than per-pixel Kotlin YUV→RGB math because
 * YuvImage and BitmapFactory run in native C code.
 */
internal actual fun CameraFrame.toImageBitmap(): ImageBitmap {
    val nv21 = nv12ToNv21(bytes, width, height)
    val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream(width * height)
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val jpegBytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    return bitmap.asImageBitmap()
}

/**
 * Swaps interleaved UV pairs from NV12 (U,V) order to NV21 (V,U) order.
 * The Y plane is identical and copied as-is.
 */
private fun nv12ToNv21(nv12: ByteArray, width: Int, height: Int): ByteArray {
    val ySize = width * height
    val nv21 = nv12.copyOf()
    // Swap each UV pair in the chroma plane
    var i = ySize
    while (i + 1 < nv21.size) {
        val tmp = nv21[i]
        nv21[i] = nv21[i + 1]
        nv21[i + 1] = tmp
        i += 2
    }
    return nv21
}
