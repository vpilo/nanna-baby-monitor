package org.vpilo.babymonitor.camera.data

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.vpilo.babymonitor.camera.data.ktx.sizes
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.DesktopVideoStream
import org.vpilo.babymonitor.model.OpaqueVideoStream
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.awt.image.LookupOp
import java.awt.image.ShortLookupTable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

internal actual class VideoCaptureDataSource(
    webcamGetter: () -> Webcam,
) {
    actual constructor() : this({ Webcam.getDefault() })

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var videoCaptureJob: Job? = null
    private var resolution: CameraResolution = CameraResolution.Medium
    private val webcam: Webcam = webcamGetter()
    private val brightnessController = AutoBrightnessController()
    private var frameCounter = 0
    private var currentGain: Float = 1f
    private var lutGain: Float = -1f
    private var lookupOp: LookupOp? = null

    private val mutableVideoStream = DesktopVideoStream()
    actual val videoStream: OpaqueVideoStream = mutableVideoStream

    init {
        mutableVideoStream.isActive
            .distinctUntilChanged()
            .onEach { active -> if (active) start() else stop() }
            .launchIn(coroutineScope)
    }

    private fun start() {
        if (videoCaptureJob?.isActive == true) {
            Logger.w(TAG) { "Camera is already running, ignoring start request." }
            return
        }

        val resolutions = resolutionsFor(resolution)
        @Suppress("SpreadOperator")
        webcam.setCustomViewSizes(*resolutions)
        for (size in resolutions) {
            webcam.setViewSize(size)
            if (webcam.open()) {
                Logger.d(TAG) { "Camera opened with ${size.sizes}" }
                break
            }
        }
        if (!webcam.isOpen) {
            webcam.setCustomViewSizes(null)
            check(webcam.open()) { "Failed to open webcam with any resolution." }
        }

        val size = webcam.viewSize
        Logger.d(TAG) {
            "Camera supports resolutions: ${webcam.viewSizes.map { it.sizes }}, current ${size.sizes}"
        }

        with(mutableVideoStream) {
            setRotation(0)
            setFrameSize(size.width, size.height)
        }

        videoCaptureJob =
            coroutineScope
                .launch { frameLoop() }
                .apply {
                    invokeOnCompletion { ex ->
                        if (ex == null || ex is CancellationException) {
                            Logger.d(TAG) { "Camera stopped" }
                        } else {
                            Logger.w(TAG, ex) { "Camera failed!" }
                        }
                        webcam.close()
                        videoCaptureJob = null
                    }
                }
        Logger.i(TAG) { "Camera started" }
    }

    private fun stop() {
        runBlocking { videoCaptureJob?.cancelAndJoin() }
        videoCaptureJob = null
    }

    actual fun setResolution(resolution: CameraResolution) {
        if (this.resolution == resolution) return
        this.resolution = resolution
        if (videoCaptureJob?.isActive == true) {
            Logger.i(TAG) { "Resolution changed to $resolution, restarting capture" }
            stop()
            start()
        }
    }

    actual fun setLowLightBoostEnabled(enabled: Boolean) = brightnessController.setEnabled(enabled)

    private fun BufferedImage.applyLowLightBoost(): BufferedImage {
        if (frameCounter++ % BRIGHTNESS_SAMPLING_INTERVAL == 0) {
            currentGain = brightnessController.update(measureLuminance(this))
        }
        val op = lookupOpFor(currentGain) ?: return this
        op.filter(raster, raster)
        return this
    }

    private fun measureLuminance(image: BufferedImage): Float {
        val width = image.width
        val height = image.height
        if (width == 0 || height == 0) return 1f

        val stepX = max(1, width / BRIGHTNESS_SAMPLE_SIZE)
        val stepY = max(1, height / BRIGHTNESS_SAMPLE_SIZE)

        var sum = 0.0
        var count = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 1f else (sum / count).toFloat()
    }

    // Build (and cache) a gamma lookup table for out = in^(1/gain). Returns null at unity gain.
    private fun lookupOpFor(gain: Float): LookupOp? {
        if (gain <= UNITY_GAIN_EPSILON) return null
        if (lookupOp == null || abs(gain - lutGain) > GAIN_LUT_EPSILON) {
            val invGamma = 1.0 / gain
            val table = ShortArray(256)
            for (i in 0..255) {
                table[i] = (255.0 * (i / 255.0).pow(invGamma)).roundToInt().coerceIn(0, 255).toShort()
            }
            lookupOp = LookupOp(ShortLookupTable(0, table), null)
            lutGain = gain
        }
        return lookupOp
    }

    private suspend fun frameLoop() {
        val maxFrameTime =
            (
                1000L /
                    when (resolution) {
                        CameraResolution.Low -> CameraConstants.MAX_FPS_LOW_QUALITY
                        CameraResolution.Medium -> CameraConstants.MAX_FPS_MEDIUM_QUALITY
                        CameraResolution.High -> CameraConstants.MAX_FPS_HIGH_QUALITY
                    }
            ).milliseconds

        while (webcam.isOpen) {
            val frameTime =
                measureTime {
                    if (!webcam.isImageNew) {
                        delay(5.milliseconds)
                        return@measureTime
                    }
                    val captured: BufferedImage? = webcam.getImage()
                    if (captured == null) {
                        Logger.w(TAG) { "Failed to capture image" }
                        delay(100.milliseconds)
                    } else {
                        mutableVideoStream.onFrame(
                            captured
                                .applyLowLightBoost()
                                .toComposeImageBitmap(),
                        )
                    }
                }

            val diff = frameTime - maxFrameTime
            if (diff.isNegative()) {
                delay(maxFrameTime - frameTime)
            }
        }
    }

    private companion object {
        private val TAG = VideoCaptureDataSource::class

        // Sample once every N captured frames.
        private const val BRIGHTNESS_SAMPLING_INTERVAL = 15

        // Approximate number of samples per axis when estimating luminance.
        private const val BRIGHTNESS_SAMPLE_SIZE = 48

        // Below this gain, skip the gamma pass entirely (treat as identity).
        private const val UNITY_GAIN_EPSILON = 1.001f

        // Rebuild the lookup table only when the gain moves by more than this.
        private const val GAIN_LUT_EPSILON = 0.02f

        private val highResolutions =
            arrayOf<Dimension>(
                WebcamResolution.FHD.size,
                WebcamResolution.WUXGA.size,
                WebcamResolution.HDP.size,
                WebcamResolution.UXGA.size,
            )

        private val mediumResolutions =
            arrayOf<Dimension>(
                WebcamResolution.HD.size,
                WebcamResolution.WXGA2.size,
                WebcamResolution.SXGA.size,
                WebcamResolution.XGA.size,
            )

        private val lowResolutions =
            arrayOf<Dimension>(
                WebcamResolution.VGA.size,
                WebcamResolution.SVGA.size,
                WebcamResolution.HVGA.size,
                WebcamResolution.QVGA.size,
            )

        private fun resolutionsFor(resolution: CameraResolution): Array<Dimension> =
            when (resolution) {
                CameraResolution.Low -> lowResolutions
                CameraResolution.Medium -> mediumResolutions + lowResolutions
                CameraResolution.High -> highResolutions + mediumResolutions + lowResolutions
            }
    }
}
