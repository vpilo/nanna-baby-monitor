package org.vpilo.babymonitor.camera.data

import android.content.Context
import android.view.Surface
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder

internal actual class VideoCaptureDataSource(
    private val mainDispatcher: CoroutineDispatcher,
) : SharedResourceHolder<CameraFrame>(
    bufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
), AndroidService {

    actual constructor() : this(mainDispatcher = Dispatchers.Main)

    actual val frames: CameraFrameFlow = collector.asSharedFlow()

    private var cameraProvider: ProcessCameraProvider? = null

    private val executor = coroutineDispatcher.asExecutor()

    private fun onFrameReceived(image: ImageProxy) {
        @OptIn(ExperimentalGetImage::class)
        val mediaImage = image.image
            ?: run {
                Logger.e(TAG) { "Invalid image received! image=${image.image}" }
                image.close()
                return
            }

        val bytes = mediaImage.copyYuvBytes()
        image.close()
        collector.tryEmit(CameraFrame(bytes = bytes, width = image.width, height = image.height))
    }

    /**
     * Copies the raw YUV_420_888 plane data into a tightly-packed NV12 byte array
     * (Y plane followed by interleaved UV, total size = width × height × 3 / 2).
     *
     * The camera's row stride may exceed the image width (padding), so each row
     * is copied individually to strip any trailing padding bytes.
     */
    private fun android.media.Image.copyYuvBytes(): ByteArray {
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val nv12 = ByteArray(width * height * 3 / 2)

        // Copy Y plane row by row, stripping row-stride padding
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv12, row * width, width)
        }

        // Copy interleaved UV rows, stripping row-stride padding.
        // The U plane buffer (pixelStride=2) contains U₀V₀U₁V₁… per row.
        val uvHeight = height / 2
        val uvWidth = width // each UV row has width bytes (width/2 U-V pairs × 2 bytes)
        val ySize = width * height
        for (row in 0 until uvHeight - 1) {
            uBuffer.position(row * uvRowStride)
            uBuffer.get(nv12, ySize + row * uvWidth, uvWidth)
        }

        // Last UV row: the U buffer is 1 byte short (missing trailing V byte).
        // Copy what's available from the U buffer, then append the last V byte.
        val lastRow = uvHeight - 1
        val lastRowOffset = ySize + lastRow * uvWidth
        uBuffer.position(lastRow * uvRowStride)
        val remaining = uBuffer.remaining()
        uBuffer.get(nv12, lastRowOffset, remaining)

        vBuffer.position(lastRow * uvRowStride + (width / 2 - 1) * vPlane.pixelStride)
        nv12[lastRowOffset + remaining] = vBuffer.get()

        return nv12
    }

    @MainThread
    fun onCameraReady(camera: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(executor, ::onFrameReceived)
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        try {
            camera.unbindAll()
            camera.bindToLifecycle(
                lifecycleOwner, cameraSelector, imageAnalysis,
            )
        } catch (ex: Exception) {
            Logger.e(this::class) { "Failed to bind camera: ${ex.message}" }
        }
    }

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                    .also {
                        CoroutineScope(mainDispatcher).launch {
                            onCameraReady(it, lifecycleOwner)
                        }
                    }
            },
            executor,
        )
    }

    override fun onServiceStopped() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    override val TAG = VideoCaptureDataSource::class
}
