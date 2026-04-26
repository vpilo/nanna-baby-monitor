package org.vpilo.babymonitor.camera.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleStartEffect
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.server_in_audio_only_mode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.camera.presentation.ktx.toImageBitmap
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Backdrop
import org.vpilo.babymonitor.presentation.composables.FpsCounter
import org.vpilo.babymonitor.presentation.composables.PannableImage
import org.vpilo.babymonitor.presentation.preview.makePlaceholderCameraFrame
import org.vpilo.babymonitor.presentation.resources.capture_audio_only
import org.vpilo.babymonitor.presentation.resources.Res as ResCommon

private const val TAG = "CameraViewFinder"

@Composable
fun CameraViewFinder(
    modifier: Modifier = Modifier,
    captureMode: CaptureMode,
    viewModel: CameraViewFinderViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val defaultFrame = if (LocalInspectionMode.current) makePlaceholderCameraFrame() else null
    var lastFrame by remember { mutableStateOf(defaultFrame) }

    LifecycleStartEffect(Unit) {
        Logger.d(TAG) { "Started showing preview" }
        val frameJob =
            scope.launch {
                viewModel.frames.collect { lastFrame = it.toImageBitmap() }
            }

        onStopOrDispose {
            Logger.d(TAG) { "Stopped showing preview" }
            frameJob.cancel()
            lastFrame = null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        lastFrame?.let { frame ->
            PannableImage(
                bitmap = frame,
                modifier = Modifier.fillMaxSize(),
            )
            FpsCounter(frameKey = frame)
        }
        if (captureMode == CaptureMode.AUDIO_ONLY) {
            Backdrop(
                modifier = Modifier.align(Alignment.Center),
            ) {
                Image(
                    painter = painterResource(ResCommon.drawable.capture_audio_only),
                    contentDescription = stringResource(Res.string.server_in_audio_only_mode),
                )
            }
        }
    }
}

@Preview
@Composable
fun CameraViewFinderNormalPreview() =
    AppPreviewTheme(
        withModule = {
            factory<VideoCaptureRepository> {
                object : VideoCaptureRepository {
                    override val frames: CameraFrameFlow = flowOf()
                }
            }
            viewModelOf(::CameraViewFinderViewModel)
        },
    ) {
        CameraViewFinder(
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }

@Preview
@Composable
fun CameraViewFinderAudioOnlyPreview() =
    AppPreviewTheme(
        withModule = {
            factory<VideoCaptureRepository> {
                object : VideoCaptureRepository {
                    override val frames: CameraFrameFlow = flowOf()
                }
            }
            viewModelOf(::CameraViewFinderViewModel)
        },
    ) {
        CameraViewFinder(
            captureMode = CaptureMode.AUDIO_ONLY,
        )
    }
