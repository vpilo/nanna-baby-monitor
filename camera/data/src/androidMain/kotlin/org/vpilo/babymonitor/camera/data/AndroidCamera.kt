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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraImageRotation
import org.vpilo.babymonitor.model.MutableVideoFeedFlow
import org.vpilo.babymonitor.model.oldPreviewFeedData
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.time.Instant

internal class AndroidCamera(
    private val videoFeed: MutableVideoFeedFlow,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null


    fun start(context: Context, lifecycleOwner: LifecycleOwner) {
        startAudio()
    }

    fun stop() {
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
                    //    previewFeed.tryEmit(buffer)
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

    private companion object {
        private val TAG = AndroidCamera::class
    }
}
