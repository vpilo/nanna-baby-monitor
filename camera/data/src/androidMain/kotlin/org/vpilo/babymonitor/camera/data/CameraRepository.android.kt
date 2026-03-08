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
        val rgbaBuffer = image.image?.planes?.get(0)?.buffer
            ?: run {
                Logger.e(TAG) { "Invalid image received! image=${image.image}, planes=${image.image?.planes?.size}" }
                image.close()
                return
            }

        val bytes = with(rgbaBuffer) { ByteArray(remaining()).also { get(it) } }
        image.close()
        collector.tryEmit(CameraFrame(bytes = bytes, width = image.width, height = image.height))
    }

    @MainThread
    fun onCameraReady(camera: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
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
