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
    private var lowLightBoostEnabled: Boolean = true

    private var videoCapture: VideoCapture<EncoderVideoOutput>? = null

    @Volatile
    private var renderer: CameraGlRenderer =
        CameraGlRenderer(
            bridgeSize = resolution.toSize(),
            onFrameRendered = { mutableVideoStream.signalFrameRendered() },
        )
        get() {
            if (field.isReleased()) {
                Logger.w(TAG) { "Renderer was released, creating a new one" }
                field =
                    CameraGlRenderer(
                        bridgeSize = resolution.toSize(),
                        onFrameRendered = { mutableVideoStream.signalFrameRendered() },
                    )
                field.setLowLightBoostEnabled(lowLightBoostEnabled)
            }
            return field
        }

    private val resolutionSelector: ResolutionSelector
        get() {
            val size = resolution.toSize()
            return ResolutionSelector
                .Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build()
        }

    private var orientationListener: OrientationEventListener? = null

    private var sensorRotationDegrees: Int = DEFAULT_SENSOR_ROTATION_DEGREES

    private val backgroundScope = CoroutineScope(backgroundDispatcher)

    init {
        // Publish the configured frame size early, so consumer surfaces are created at the correct dimensions.
        // This avoids that surfaces are created with an unexpected size (or better: with an unexpected aspect ratio).
        refreshCurrentFrameSize()

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

        // Lock targetRotation so CameraX adds no rotation of its own: device rotation is carried as stream metadata instead, to allow
        // clients to rotate independently of the stream.
        // The relative rotation must be zero to keep the image the "right" way up, which CameraX achieves when targetRotation degrees
        // equal the sensor mount degrees.
        // Note that the sensor-mount transposition still lives in the buffer, and is handled by sizing the surfaces to the content's
        // true aspect.
        sensorRotationDegrees = cameraInfo.sensorRotationDegrees
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

        val sensorResolution = videoCapture.resolutionInfo?.resolution ?: resolution.toSize()
        swapIfLandscape(sensorResolution).let { size ->
            Logger.d(TAG) { "Camera bound, sensor=$sensorRotationDegrees° sensorResolution=$sensorResolution content=$size" }
            mutableVideoStream.setFrameSize(size.width, size.height)
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
        refreshCurrentFrameSize()
        val provider = cameraProvider
        val context = serviceContext
        val owner = serviceLifecycleOwner
        if (provider != null && context != null && owner != null) {
            Logger.i(TAG) { "Resolution changed to $resolution" }
            owner.lifecycleScope.launch(mainDispatcher) { bind(provider, context, owner) }
        }
    }

    actual fun setLowLightBoostEnabled(enabled: Boolean) {
        lowLightBoostEnabled = enabled
        renderer.setLowLightBoostEnabled(enabled)
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
            // Do not reset the frame size to re-create surfaces already correctly on the next start.
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

    private fun refreshCurrentFrameSize() {
        val size = swapIfLandscape(resolution.toSize())
        mutableVideoStream.setFrameSize(size.width, size.height)
    }

    // CameraX delivers content made upright to the sensor's natural orientation: a 90°/270°-mounted
    // sensor (the usual case) yields portrait content. The published frame size sizes both the
    // viewfinder and encoder surfaces, so it must swap the sensor-native dimensions to match the
    // content's true aspect; otherwise the GL full-quad squeezes it (anamorphic distortion).
    private fun swapIfLandscape(size: Size): Size =
        if (sensorRotationDegrees == 90 || sensorRotationDegrees == 270) Size(size.height, size.width) else size

    private fun CameraResolution.toSize(): Size =
        when (this) {
            CameraResolution.Low -> Size(640, 480)
            CameraResolution.Medium -> Size(1280, 720)
            CameraResolution.High -> Size(1920, 1080)
        }

    private fun sensorRotationToSurfaceRotation(sensorDegrees: Int): Int =
        when (sensorDegrees) {
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }

    private companion object {
        private val TAG = VideoCaptureDataSource::class

        // Default rotation assumed for a typical phone back-camera mount.
        // This is assumed before the camera binds, to align the initial frame size to the most common case, and not need to recreate
        // the surface.
        private const val DEFAULT_SENSOR_ROTATION_DEGREES = 90
    }
}
