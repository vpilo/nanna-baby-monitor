package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.pairing_qr_wrong_device
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.RequestCameraPermission
import org.vpilo.babymonitor.camera.presentation.hasCameraPermission
import org.vpilo.babymonitor.network.common.crypto.decodePairingQrPayloadOrNull
import java.util.concurrent.Executors

@Composable
actual fun PinEntrySection(
    modifier: Modifier,
    expectedDeviceId: String,
    onPinEntered: (pin: String) -> Unit,
) {
    // hasCameraPermission() reads the OS permission state directly and isn't observable Compose
    // state, so granting the permission wouldn't by itself trigger recomposition past this gate;
    // `permissionGranted` forces that recomposition, mirroring CameraPermissionCheckScreen's pattern.
    var permissionGranted by remember { mutableStateOf(false) }
    if (!hasCameraPermission() && !permissionGranted) {
        RequestCameraPermission(onGranted = { permissionGranted = true }, onDenied = {})
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var wrongDeviceScanned by remember { mutableStateOf(false) }
    // Guards against onPinEntered firing more than once: ImageAnalysis keeps delivering frames
    // while a previous frame's async MLKit decode is still in flight, so several frames can
    // decode the same valid QR before the caller reacts and unmounts this composable.
    var hasSubmittedPin by remember { mutableStateOf(false) }
    val scanner =
        remember {
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
                    val analysis =
                        ImageAnalysis.Builder().build().apply {
                            setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage == null || hasSubmittedPin) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                scanner
                                    .process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
                                    .addOnSuccessListener { barcodes ->
                                        if (hasSubmittedPin) return@addOnSuccessListener
                                        val payload = barcodes.firstOrNull()?.rawValue?.decodePairingQrPayloadOrNull()
                                        when {
                                            payload == null -> {
                                                // Undecodable / unrelated QR code — ignore it silently.
                                            }

                                            payload.deviceId.toString() != expectedDeviceId -> {
                                                wrongDeviceScanned = true
                                            }

                                            else -> {
                                                hasSubmittedPin = true
                                                onPinEntered(payload.pin)
                                            }
                                        }
                                    }.addOnCompleteListener { imageProxy.close() }
                            }
                        }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(context))
                previewView
            },
        )
        if (wrongDeviceScanned) {
            Text(
                modifier = Modifier.align(Alignment.BottomCenter),
                text = stringResource(Res.string.pairing_qr_wrong_device),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
