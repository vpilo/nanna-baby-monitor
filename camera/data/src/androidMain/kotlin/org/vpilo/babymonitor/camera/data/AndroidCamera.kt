package org.vpilo.babymonitor.camera.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.camera.model.CameraFrameData
import org.vpilo.babymonitor.camera.model.CameraImageRotation
import org.vpilo.babymonitor.common.Logger
import java.nio.ByteBuffer
import kotlin.time.Instant

internal class AndroidCamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val videoFrames: MutableSharedFlow<CameraFrameData>,
    private val audioSamples: MutableSharedFlow<ByteArray>,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null

    private val executor = backgroundDispatcher.asExecutor()

    private val frameCount = 10
    private var frameCounter = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    // FIXME android version needs to request permissions via compose
    private fun onFrameReceived(image: ImageProxy) {
        @OptIn(ExperimentalGetImage::class)
        val rgbaBuffer = image.image?.planes?.get(0)?.buffer
            ?: run {
                Logger.e(this::class) { "Invalid image received! image=${image.image}, planes=${image.image?.planes?.size}" }
                image.close()
                return
            }

        CameraFrameData(
            width = image.width,
            height = image.height,
            rotation = when (image.imageInfo.rotationDegrees) {
                0 -> CameraImageRotation.ROTATION_0
                90 -> CameraImageRotation.ROTATION_90
                180 -> CameraImageRotation.ROTATION_180
                270 -> CameraImageRotation.ROTATION_270
                else -> CameraImageRotation.ROTATION_0
            },
            timestamp = Instant.fromEpochMilliseconds(image.imageInfo.timestamp),
            data = rgbaBuffer.toRgbaByteArray(),
        )
            .also { frame -> videoFrames.tryEmit(frame) }

        if (++frameCounter % frameCount == 0) {
            frameCounter = 0
            val now = System.currentTimeMillis()
            val delta = now - lastFpsTimestamp
            val fps = 1000 * frameCount.toFloat() / delta
            Logger.d(this::class) { "FPS: ${"%.02f".format(fps)} with ${image.width}x${image.height} image" }
            lastFpsTimestamp = now
        }
        image.close()
    }

    @MainThread
    fun onCameraReady(camera: ProcessCameraProvider) {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(executor, ::onFrameReceived)
            }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        try {
            camera.unbindAll()
            camera.bindToLifecycle(
                lifecycleOwner, cameraSelector, imageAnalysis,
            )
        } catch (ex: Exception) {
            Logger.e(this::class) { "Failed to bind camera: ${ex.message}" }
        }
    }

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                    .also {
                        CoroutineScope(mainDispatcher).launch {
                            onCameraReady(it)
                        }
                    }
            },
            executor,
        )
        startAudio()
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        stopAudio()
    }

    private fun startAudio() {
        val audioSource = MediaRecorder.AudioSource.MIC
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        audioJob = CoroutineScope(backgroundDispatcher).launch {
            val buffer = ByteArray(bufferSize)
            audioRecord?.startRecording()
            while (isActive) {
                audioRecord?.read(buffer, 0, buffer.size)?.let { read ->
                    if (isActive && read > 0) {
                        audioSamples.tryEmit(buffer)
                    }
                }
            }
        }
    }

    private fun stopAudio() {
        audioJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun ByteBuffer.toRgbaByteArray(): ByteArray =
        ByteArray(remaining()).also { get(it) }
}
