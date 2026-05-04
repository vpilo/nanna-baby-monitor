package org.vpilo.babymonitor.camera.data

import android.content.Context
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import androidx.annotation.MainThread
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.SessionConfig
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import java.lang.ref.WeakReference
import kotlin.math.abs

internal actual class VideoCaptureDataSource(
    private val mainDispatcher: CoroutineDispatcher,
) : SharedResourceHolder<CameraFrame>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
    ),
    AndroidService {
    actual constructor() : this(mainDispatcher = Dispatchers.Main)

    actual val frames: CameraFrameFlow = collector.asSharedFlow()

    private var cameraProvider: ProcessCameraProvider? = null

    private val executor = coroutineDispatcher.asExecutor()

    private var resolution: CameraResolution = CameraResolution.Medium

    private val resolutionSelector: ResolutionSelector by lazy {
        val size =
            when (resolution) {
                CameraResolution.Low -> Size(640, 480)
                CameraResolution.Medium -> Size(1280, 720)
                CameraResolution.High -> Size(1920, 1080)
            }
        ResolutionSelector
            .Builder()
            .setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
    }

    private var orientationListener: OrientationEventListener? = null

    /**
     * Converts a YUV_420_888 [ImageProxy] to tightly-packed NV12 bytes
     * (Y plane followed by interleaved UV pairs), respecting per-plane
     * row strides and pixel strides so it works on all devices.
     */
    private fun onFrameReceived(image: ImageProxy) {
        val w = image.width
        val h = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        // Tightly-packed NV12: w*h Y bytes + w*h/2 interleaved UV bytes
        val nv12 = ByteArray(w * h * 3 / 2)

        // Copy Y plane row-by-row, stripping any padding
        var destPos = 0
        for (row in 0 until h) {
            yBuf.position(row * yRowStride)
            yBuf.get(nv12, destPos, w)
            destPos += w
        }

        // Copy UV planes interleaved as NV12 (U, V, U, V, …)
        val uvHeight = h / 2
        val uvWidth = w / 2
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val uvIndex = row * uvRowStride + col * uvPixelStride
                nv12[destPos++] = uBuf.get(uvIndex)
                nv12[destPos++] = vBuf.get(uvIndex)
            }
        }

        image.close()
        collector.tryEmit(CameraFrame(bytes = nv12, width = w, height = h))
    }

    @MainThread
    @OptIn(ExperimentalSessionConfig::class)
    fun onCameraReady(
        cameraProvider: ProcessCameraProvider,
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        val imageAnalyzer =
            ImageAnalysis
                .Builder()
                .setOutputImageRotationEnabled(true)
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackgroundExecutor(executor)
                .build()
                .also {
                    it.setAnalyzer(executor, ::onFrameReceived)
                }
        val cameraSelector =
            CameraSelector
                .Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
        val supportedFpsRanges = cameraProvider.getCameraInfo(cameraSelector).supportedFrameRateRanges.sortedBy { it.upper }
        val desiredFpsRange =
            when (resolution) {
                CameraResolution.Low -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_LOW_QUALITY)
                CameraResolution.Medium -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_MEDIUM_QUALITY)
                CameraResolution.High -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_HIGH_QUALITY)
            }
        val fpsRange =
            desiredFpsRange
                .takeIf { supportedFpsRanges.contains(it) }
                ?: run {
                    supportedFpsRanges
                        .map { it to abs(it.lower - desiredFpsRange.lower + it.upper - desiredFpsRange.upper) }
                        .also {
                            Logger.w(TAG) {
                                "FPS range $desiredFpsRange not supported, selecting one from: ${it.joinToString(", ")}"
                            }
                        }.minBy { it.second }
                        .first
                }
        Logger.i(TAG) { "Selected FPS range: $fpsRange" }

        val sessionConfig =
            SessionConfig(
                useCases = listOf(imageAnalyzer),
                frameRateRange = fpsRange,
            )
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                sessionConfig,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            Logger.e(TAG) { "Failed to bind camera: ${ex.prettify()}" }
            return
        }

        orientationListener =
            object : OrientationEventListener(context) {
                init {
                    enable()
                }

                private var lastRotation = ORIENTATION_UNKNOWN
                private val target = WeakReference(imageAnalyzer)

                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN || orientation == lastRotation) return
                    lastRotation = orientation
                    target
                        .get()
                        ?.setTargetRotation(UseCase.snapToSurfaceRotation(orientation))
                        ?: this.disable()
                }
            }
    }

    actual fun setResolution(resolution: CameraResolution) {
        if (this.resolution == resolution) return
        this.resolution = resolution
        if (isActiveNow) {
            Logger.i(TAG) { "Resolution changed to $resolution, restarting capture" }
            stop()
            start()
        }
    }

    override fun onServiceStarted(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider =
                    cameraProviderFuture
                        .get()
                        .also {
                            CoroutineScope(mainDispatcher).launch {
                                onCameraReady(it, context, lifecycleOwner)
                            }
                        }
            },
            executor,
        )
    }

    override fun onServiceStopped() {
        CoroutineScope(mainDispatcher).launch {
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }
}
