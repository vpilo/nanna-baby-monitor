package org.vpilo.babymonitor.camera.presentation.pairing

import android.content.Context
import android.graphics.ImageFormat
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

@Stable
actual class CameraQrScannerViewModel actual constructor() : AppViewModel<Unit, Unit, CameraQrScannerEffect>(initialState = Unit) {
    private val qrReader = QrReader()

    override fun SubscriptionScope.onSubscribed() {
        qrReader.scannedDataFlow
            .subscribe {
                Logger.d(TAG) { "QR code scan: $it" }
                it ?: return@subscribe
                CameraQrScannerEffect.QrScanned(it).sendEffect()
                // VALERIO pause scanning after success
            }
    }

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val cameraPreviewUseCase =
        Preview
            .Builder()
            .build()
            .apply {
                setSurfaceProvider { newSurfaceRequest ->
                    _surfaceRequest.update { newSurfaceRequest }
                }
            }

    private val analysisUseCase = ImageAnalysis.Builder().build()

    private val analyzer =
        ImageAnalysis.Analyzer { imageProxy ->
            // VALERIO stop entire composable on error
            val image =
                @ExperimentalGetImage
                imageProxy.image
            if (image == null) {
                Logger.w(TAG) { "No image" }
                imageProxy.close()
                return@Analyzer
            }

            if (image.format != ImageFormat.YUV_420_888) {
                Logger.w(TAG) { "Unsupported image format: ${image.format}" }
                imageProxy.close()
                CameraQrScannerEffect.CameraError.sendEffect()
                return@Analyzer
            }

            try {
                // Use directly the Y plane, the first in the `planes` array of a YUV_420_888 image.
                // It contains the luminance data for QR analysis.
                val plane = image.planes[0]
                val buffer =
                    plane.buffer
                        .apply { rewind() }
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val luminanceSource =
                    PlanarYUVLuminanceSource(
                        bytes,
                        plane.rowStride,
                        image.height,
                        0,
                        0,
                        image.width,
                        image.height,
                        false,
                    )
                qrReader.readFrame(BinaryBitmap(HybridBinarizer(luminanceSource)))
            } catch (_: Exception) {
                // Ignore image decoding errors.
            } finally {
                imageProxy.close()
            }
        }

    suspend fun bindToCamera(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        Logger.d(TAG) { "BINDING" }
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(appContext), analyzer)

        processCameraProvider.bindToLifecycle(
            lifecycleOwner,
            DEFAULT_BACK_CAMERA,
            cameraPreviewUseCase,
            analysisUseCase,
        )

        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }
    }
}
