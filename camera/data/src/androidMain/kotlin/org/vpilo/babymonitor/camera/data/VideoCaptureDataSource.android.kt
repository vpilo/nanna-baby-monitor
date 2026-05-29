package org.vpilo.babymonitor.camera.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.MainThread
import androidx.camera.core.CameraSelector
import androidx.camera.core.SessionConfig
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.VideoCapture
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.AndroidServerVideoStream
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.OpaqueVideoStream
import kotlin.math.abs

internal actual class VideoCaptureDataSource(
    private val mainDispatcher: CoroutineDispatcher,
    backgroundDispatcher: CoroutineDispatcher,
) : AndroidService {
    actual constructor() : this(Dispatchers.Main, Dispatchers.Default)

    private val mutableVideoStream = AndroidServerVideoStream()
    actual val videoStream: OpaqueVideoStream = mutableVideoStream

    override val role: AppRole = AppRole.SERVER

    private var cameraProvider: ProcessCameraProvider? = null
    private var serviceContext: Context? = null
    private var serviceLifecycleOwner: LifecycleOwner? = null

    private val mainExecutor = mainDispatcher.asExecutor()

    private var resolution: CameraResolution = CameraResolution.Medium

    private var videoCapture: VideoCapture<EncoderVideoOutput>? = null

    @Volatile
    private var renderer: CameraGlRenderer =
        CameraGlRenderer(
            bridgeSize = resolutionToSize(resolution),
            onFrameRendered = { mutableVideoStream.signalFrameRendered() },
        )
        get() {
            if (field.isReleased()) {
                Logger.w(TAG) { "Renderer was released, creating a new one" }
                field =
                    CameraGlRenderer(
                        bridgeSize = resolutionToSize(resolution),
                        onFrameRendered = { mutableVideoStream.signalFrameRendered() },
                    )
            }
            return field
        }

    private val resolutionSelector: ResolutionSelector
        get() {
            val size = resolutionToSize(resolution)
            return ResolutionSelector
                .Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build()
        }

    private var orientationListener: OrientationEventListener? = null

    private val backgroundScope = CoroutineScope(backgroundDispatcher)

    init {
        // Publish the configured frame size up front so the viewfinder surface is created at the
        // correct dimensions. Creating that surface is what starts the camera (isActive =
        // surface != null), so the real resolutionInfo size isn't known yet at surface creation,
        // and the EGL window surface locks to whatever size it is created with.
        publishConfiguredFrameSize()

        backgroundScope.launch {
            mutableVideoStream.isActive.collect { active ->
                Logger.d(TAG) { "Data source is ${if (active) "starting" else "stopping"}" }
                if (active) {
                    AndroidServiceRegistry.register(this@VideoCaptureDataSource)
                } else {
                    AndroidServiceRegistry.unregister(this@VideoCaptureDataSource)
                }
            }
        }
        // Drive renderer attach/detach for the encoder.
        backgroundScope.launch {
            mutableVideoStream.encoderSurface.collect { surface ->
                with(renderer) {
                    if (surface != null) {
                        attachEncoder(surface)
                        Logger.d(TAG) { "Encoder attached" }
                    } else {
                        detachEncoder()
                        Logger.d(TAG) { "Encoder detached" }
                    }
                }
            }
        }
        // Drive renderer attach/detach for the viewfinder.
        backgroundScope.launch {
            mutableVideoStream.surface.collect { surface ->
                with(renderer) {
                    if (surface != null) {
                        Logger.d(TAG) { "Viewfinder attached" }
                        attachViewfinder(surface)
                    } else {
                        Logger.d(TAG) { "Viewfinder detached" }
                        detachViewfinder()
                    }
                }
            }
        }
    }

    @MainThread
    private fun bind(
        cameraProvider: ProcessCameraProvider,
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        val selector = resolutionSelector

        val cameraSelector =
            CameraSelector
                .Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

        val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)

        // Lock targetRotation so CameraX applies no rotation between sensor and consumer,
        // keeping the buffer sensor-native landscape regardless of device orientation.
        // CameraX computes relativeRotation = (sensorMount + targetDegrees) % 360;
        // we want zero, so targetDegrees = (360 - sensorMount) % 360.
        val sensorTargetRotation = sensorRotationToSurfaceRotation(cameraInfo.sensorRotationDegrees)

        val videoCapture =
            @SuppressLint("RestrictedApi")
            VideoCapture
                .Builder(EncoderVideoOutput(resolution, renderer, mainExecutor))
                .setResolutionSelector(selector)
                .setTargetRotation(sensorTargetRotation)
                .build()

        val supportedFpsRanges =
            cameraInfo
                .supportedFrameRateRanges
                .sortedBy { it.upper }
        val desiredFpsRange =
            when (resolution) {
                CameraResolution.Low -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_LOW_QUALITY)
                CameraResolution.Medium -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_MEDIUM_QUALITY)
                CameraResolution.High -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_HIGH_QUALITY)
            }
        val fpsRange =
            desiredFpsRange
                .takeIf { supportedFpsRanges.contains(it) }
                ?: supportedFpsRanges
                    .map { it to abs(it.lower - desiredFpsRange.lower + it.upper - desiredFpsRange.upper) }
                    .also {
                        Logger.w(TAG) {
                            "FPS range $desiredFpsRange not supported, selecting one from: ${it.joinToString(", ")}"
                        }
                    }.minBy { it.second }
                    .first
        Logger.i(TAG) { "Selected FPS range: $fpsRange" }

        val sessionConfig =
            SessionConfig(
                useCases = listOf(videoCapture),
                frameRateRange = fpsRange,
            )

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, sessionConfig)
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            Logger.e(TAG) { "Failed to bind camera: ${ex.prettify()}" }
            return
        }

        this.videoCapture = videoCapture

        (videoCapture.resolutionInfo?.resolution ?: resolutionToSize(resolution))
            .let { resolution ->
                Logger.d(TAG) { "Camera bound with size $resolution" }
                mutableVideoStream.setFrameSize(resolution.width, resolution.height)
            }

        orientationListener?.disable()
        orientationListener =
            object : OrientationEventListener(context) {
                private var lastRotationDegrees = -1

                init {
                    enable()
                }

                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return

                    val rotation = ((orientation + 45) / 90 * 90) % 360
                    if (rotation == lastRotationDegrees) return
                    lastRotationDegrees = rotation

                    Logger.i(TAG) { "Device rotation changed: $rotation°" }
                    mutableVideoStream.setRotation(rotation)
                }
            }
    }

    actual fun setResolution(resolution: CameraResolution) {
        if (this.resolution == resolution) return
        this.resolution = resolution
        publishConfiguredFrameSize()
        val provider = cameraProvider
        val context = serviceContext
        val owner = serviceLifecycleOwner
        if (provider != null && context != null && owner != null) {
            Logger.i(TAG) { "Resolution changed to $resolution" }
            owner.lifecycleScope.launch(mainDispatcher) { bind(provider, context, owner) }
        }
    }

    override fun onServiceStarted(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        serviceContext = context
        serviceLifecycleOwner = lifecycleOwner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                lifecycleOwner.lifecycleScope.launch(mainDispatcher) {
                    bind(provider, context, lifecycleOwner)
                }
            },
            mainExecutor,
        )
    }

    override fun onServiceStopped() {
        serviceLifecycleOwner?.lifecycleScope?.launch(mainDispatcher) {
            // Keep the configured frame size published so the viewfinder surface is recreated at
            // the right size on the next start; the server's size is its configured resolution,
            // not a streaming-only value.
            mutableVideoStream.setRotation(0)
            cameraProvider?.unbindAll()
            cameraProvider = null
            videoCapture = null
            orientationListener?.disable()
            orientationListener = null
            renderer.release()
            serviceContext = null
            serviceLifecycleOwner = null
        }
    }

    private fun publishConfiguredFrameSize() {
        val size = resolutionToSize(resolution)
        mutableVideoStream.setFrameSize(size.width, size.height)
    }

    private fun resolutionToSize(resolution: CameraResolution): Size =
        when (resolution) {
            CameraResolution.Low -> Size(640, 480)
            CameraResolution.Medium -> Size(1280, 720)
            CameraResolution.High -> Size(1920, 1080)
        }

    private fun sensorRotationToSurfaceRotation(sensorDegrees: Int): Int =
        when (sensorDegrees) {
            90 -> Surface.ROTATION_270
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }

    private companion object {
        private val TAG = VideoCaptureDataSource::class
    }
}
