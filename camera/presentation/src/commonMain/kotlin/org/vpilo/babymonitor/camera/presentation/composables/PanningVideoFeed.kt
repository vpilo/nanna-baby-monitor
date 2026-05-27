package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.server_in_audio_only_mode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Backdrop
import org.vpilo.babymonitor.presentation.composables.FpsCounter
import org.vpilo.babymonitor.presentation.preview.ComposePreviewVideoStream
import org.vpilo.babymonitor.presentation.preview.makePreviewVideoStream
import org.vpilo.babymonitor.presentation.resources.capture_audio_only
import org.vpilo.babymonitor.presentation.resources.Res as ResPresentation

private const val TAG = "PanningVideoFeed"

@Composable
fun PanningVideoFeed(
    modifier: Modifier = Modifier,
    captureMode: CaptureMode,
    videoStream: OpaqueVideoStream,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val originalFrameSize by videoStream.frameSize.collectAsState()
    val rotation by videoStream.rotation.collectAsState()
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val frameSize =
        remember(originalFrameSize, rotation) {
            if (rotation == 90 || rotation == 270) IntSize(originalFrameSize.height, originalFrameSize.width) else originalFrameSize
        }

    // The container should always be filled, clipping any overflow on the longer axis.
    val cropScale =
        remember(containerSize, frameSize) {
            if (containerSize == IntSize.Zero || frameSize == IntSize.Zero) {
                1f
            } else {
                maxOf(
                    containerSize.width.toFloat() / frameSize.width.toFloat(),
                    containerSize.height.toFloat() / frameSize.height.toFloat(),
                )
            }
        }

    val maxPanningAllowed =
        remember(containerSize, frameSize, cropScale) {
            Offset(
                x = ((cropScale * frameSize.width - containerSize.width) / 2f).coerceAtLeast(0f),
                y = ((cropScale * frameSize.height - containerSize.height) / 2f).coerceAtLeast(0f),
            )
        }

    Logger.d(TAG) {
        "frame=$originalFrameSize container=$containerSize" +
            " rotation=$rotation cropScale=$cropScale pan=$panOffset maxPan=$maxPanningAllowed"
    }

    LaunchedEffect(rotation) { panOffset = Offset.Zero }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .clipToBounds()
                .pointerInput(maxPanningAllowed) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        panOffset =
                            Offset(
                                x = (panOffset.x - drag.x).coerceIn(-maxPanningAllowed.x, maxPanningAllowed.x),
                                y = (panOffset.y - drag.y).coerceIn(-maxPanningAllowed.y, maxPanningAllowed.y),
                            )
                    }
                },
    ) {
        if (LocalInspectionMode.current) {
            require(videoStream is ComposePreviewVideoStream)
            val frame by videoStream.surface.collectAsState(ImageBitmap(1, 1))
            Image(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset(x = panOffset.x.dp, y = panOffset.y.dp),
                bitmap = frame,
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        } else {
            PanningVideoFeedContent(
                modifier = Modifier.fillMaxSize(),
                videoStream = videoStream,
                originalFrameSize = originalFrameSize,
                containerSize = containerSize,
                maxPanningAllowed = maxPanningAllowed,
                cropScale = cropScale,
                panOffset = panOffset,
                rotation = rotation,
            )
        }
        if (captureMode == CaptureMode.AUDIO_ONLY) {
            Backdrop(modifier = Modifier.align(Alignment.Center)) {
                Image(
                    painter = painterResource(ResPresentation.drawable.capture_audio_only),
                    contentDescription = stringResource(Res.string.server_in_audio_only_mode),
                )
            }
        }
        FpsCounter(
            modifier = Modifier.align(Alignment.BottomEnd),
            videoStream = videoStream,
        )
    }
}

@Preview
@Composable
private fun PanningVideoFeedPreview() =
    AppPreviewTheme {
        PanningVideoFeed(
            videoStream = makePreviewVideoStream(),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }
