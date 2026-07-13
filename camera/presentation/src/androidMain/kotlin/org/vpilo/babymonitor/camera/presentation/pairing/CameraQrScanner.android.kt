package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.vpilo.babymonitor.network.common.crypto.decodePairingQrPayloadOrNull

@Composable
actual fun CameraQrScanner(
    modifier: Modifier,
    onPinEntered: (pin: String, deviceId: String) -> Unit,
) {
    // Guards against onPinEntered firing more than once: ImageAnalysis keeps delivering frames
    // while a previous frame's async MLKit decode is still in flight, so several frames can
    // decode the same valid QR before the caller reacts and unmounts this composable.
    var hasSubmittedPin by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasSubmittedPin) return@Box
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
