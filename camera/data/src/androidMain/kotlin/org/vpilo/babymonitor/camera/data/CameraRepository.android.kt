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
import org.vpilo.babymonitor.model.CameraFrameRepository
import org.vpilo.babymonitor.model.Configuration
import kotlin.reflect.KClass

actual class CameraRepository(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : CameraFrameRepository,
    SharedResourceRepository<CameraFrame>(
        bufferCapacity = Configuration.MAX_FRAME_BUFFER_SIZE,
    ), AndroidService {

    override val frames: CameraFrameFlow = collector.asSharedFlow()

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

        // YUV_420_888: plane 0 = Y, plane 1 = U, plane 2 = V
        val yPlane = mediaImage.planes[0]
        val uPlane = mediaImage.planes[1]
        val vPlane = mediaImage.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // NV21 interleaving: VUVU...
        // If pixelStride is 2, the UV planes are already semi-planar (NV21-like)
        if (vPlane.pixelStride == 2) {
            // V and U planes are interleaved — copy V plane which includes U data in NV21 order
            vBuffer.get(nv21, ySize, vSize)
        } else {
            // Planar UV — manually interleave as NV21 (V first, then U)
            val uBytes = ByteArray(uSize)
            val vBytes = ByteArray(vSize)
            uBuffer.get(uBytes)
            vBuffer.get(vBytes)
            var offset = ySize
            for (i in vBytes.indices) {
                nv21[offset++] = vBytes[i]
                if (i < uBytes.size) {
                    nv21[offset++] = uBytes[i]
                }
            }
        }

        image.close()
        collector.tryEmit(CameraFrame(bytes = nv21, width = image.width, height = image.height))
    }

    @MainThread
    fun onCameraReady(camera: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
        Logger.d(TAG) { "Starting recording" }
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
        Logger.d(TAG) { "Stopping recording" }
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    override val TAG: KClass<*> = CameraRepository::class
}
