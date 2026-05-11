package org.vpilo.babymonitor.camera.data

import androidx.compose.ui.graphics.ImageBitmap
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
import kotlin.coroutines.cancellation.CancellationException
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
                    val image: ImageBitmap? = webcam.getImage()?.toComposeImageBitmap()
                    if (image == null) {
                        Logger.w(TAG) { "Failed to capture image" }
                        delay(100.milliseconds)
                    } else {
                        mutableVideoStream.onFrame(image)
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
