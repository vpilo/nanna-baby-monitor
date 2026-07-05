package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.server_in_audio_only_mode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Backdrop
import org.vpilo.babymonitor.presentation.composables.FpsCounter
import org.vpilo.babymonitor.presentation.composables.Tooltip
import org.vpilo.babymonitor.presentation.preview.ComposePreviewVideoStream
import org.vpilo.babymonitor.presentation.preview.makePreviewVideoStream
import org.vpilo.babymonitor.presentation.resources.capture_audio_only
import org.vpilo.babymonitor.presentation.resources.Res as ResPresentation

@Composable
fun PanningVideoFeed(
    modifier: Modifier = Modifier,
    captureMode: CaptureMode,
    videoStream: OpaqueVideoStream?,
) {
    if (captureMode == CaptureMode.AUDIO_ONLY || videoStream == null) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Backdrop(modifier = Modifier.align(Alignment.Center)) {
                Tooltip(text = stringResource(Res.string.server_in_audio_only_mode)) {
                    Image(
                        painter = painterResource(ResPresentation.drawable.capture_audio_only),
                        contentDescription = null,
                    )
                }
            }
        }
        return
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val originalFrameSize by videoStream.frameSize.collectAsState()
    val rotation by videoStream.rotation.collectAsState()
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val params =
        remember(containerSize, originalFrameSize, rotation) {
            ViewfinderParams.compute(containerSize, originalFrameSize, rotation)
        }

    LaunchedEffect(rotation, containerSize) { panOffset = Offset.Zero }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isFeedActive = lifecycleState.isAtLeast(minLifecycleStateForVideoFeed)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
                .clipToBounds()
                .pointerInput(params.maxPan) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        panOffset =
                            Offset(
                                x = (panOffset.x - drag.x).coerceIn(-params.maxPan.x, params.maxPan.x),
                                y = (panOffset.y - drag.y).coerceIn(-params.maxPan.y, params.maxPan.y),
                            )
                    }
                },
    ) {
        if (LocalInspectionMode.current) {
            require(videoStream is ComposePreviewVideoStream)
            val frame by videoStream.surface.collectAsState(ImageBitmap(1, 1))
            PannableVideoFrame(
                modifier = Modifier.fillMaxSize(),
                frame = frame,
                originalFrameSize = originalFrameSize,
                cropScale = params.cropScale,
                panOffset = panOffset,
                rotation = rotation,
            )
        } else if (isFeedActive) {
            PanningVideoFeedContent(
                modifier = Modifier.fillMaxSize(),
                videoStream = videoStream,
                originalFrameSize = originalFrameSize,
                containerSize = containerSize,
                maxPanningAllowed = params.maxPan,
                cropScale = params.cropScale,
                panOffset = panOffset,
                rotation = rotation,
            )
        }
        FpsCounter(
            modifier = Modifier.align(Alignment.BottomEnd),
            videoStream = videoStream,
        )
    }
}

@Preview(widthDp = 1080, heightDp = 720)
@Composable
private fun PanningVideoFeedLandscapePreview() =
    AppPreviewTheme {
        PanningVideoFeed(
            videoStream = makePreviewVideoStream(rotation = 90),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }

@Preview(widthDp = 1080, heightDp = 720)
@Composable
private fun PanningVideoFeedLandscapeFlippedPreview() =
    AppPreviewTheme {
        PanningVideoFeed(
            videoStream = makePreviewVideoStream(rotation = 270),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }

@Preview
@Composable
private fun PanningVideoFeedPortraitPreview() =
    AppPreviewTheme {
        PanningVideoFeed(
            videoStream = makePreviewVideoStream(rotation = 0),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }

@Preview
@Composable
private fun PanningVideoFeedPortraitFlippedPreview() =
    AppPreviewTheme {
        PanningVideoFeed(
            videoStream = makePreviewVideoStream(rotation = 180),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }

@Preview
@Composable
private fun PanningVideoFeedNoVideoPreview() =
    AppPreviewTheme {
        PanningVideoFeed(
            videoStream = makePreviewVideoStream(rotation = 0),
            captureMode = CaptureMode.AUDIO_ONLY,
        )
    }
