package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
actual fun CameraQrScanner(
    modifier: Modifier,
    viewModel: CameraQrScannerViewModel,
    onQrRead: (qr: String) -> Unit,
    onError: () -> Unit,
) {
    @Suppress("UnusedVariable", "UnusedPrivateProperty", "unused")
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    LaunchedEffect(viewModel) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is CameraQrScannerEffect.QrScanned -> {
                    onQrRead(effect.qr)
                }

                is CameraQrScannerEffect.CameraError -> {
                    onError()
                }
            }
        }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            modifier = modifier,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
//        if (hasSubmittedPin) return@Box
        // VALERIO next commit
//        ScannerWithPermissions(
//            types = listOf(CodeType.QR),
//            enableTorch = false,
//            onScanned = {
//                if (hasSubmittedPin) return@ScannerWithPermissions false
//                val payload = it.decodePairingQrPayloadOrNull()
//                when {
//                    payload == null -> {
//                        // Undecodable / unrelated QR code — ignore it silently.
//                    }
//
//                    else -> {
//                        hasSubmittedPin = true
//                        onPinEntered(payload.pin)
//                    }
//                }
//                println(it); true
//            },
//        )
    }
}
