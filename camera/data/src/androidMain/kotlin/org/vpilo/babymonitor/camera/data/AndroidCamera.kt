package org.vpilo.babymonitor.camera.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
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
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrameData
import org.vpilo.babymonitor.model.CameraImageRotation
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.time.Instant

internal class AndroidCamera(
    private val videoFrames: MutableSharedFlow<CameraFrameData>,
    private val audioSamples: MutableSharedFlow<ByteArray>,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null

    private val executor = backgroundDispatcher.asExecutor()

    private fun onFrameReceived(image: ImageProxy) {
        @OptIn(ExperimentalGetImage::class)
        val rgbaBuffer = image.image?.planes?.get(0)?.buffer
            ?: run {
                Logger.e(this::class) { "Invalid image received! image=${image.image}, planes=${image.image?.planes?.size}" }
                image.close()
                return
            }
        val bytes = rgbaBuffer.toJpeg(image.width, image.height)
        image.close()

        videoFrames.tryEmit(
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
                data = bytes,
            ),
        )
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

    fun start(context: Context, lifecycleOwner: LifecycleOwner) {
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
        startAudio()
    }

    fun stop() {
        Logger.d(TAG) { "Stopping recording" }
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

    private fun ByteBuffer.toJpeg(width: Int, height: Int, quality: Int = JPEG_QUALITY): ByteArray {
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(this)
        return ByteArrayOutputStream()
            .let {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
                bitmap.recycle()
                it.toByteArray()
            }
    }

    private companion object {
        private const val JPEG_QUALITY = 70

        private val TAG = AndroidCamera::class
    }
}
