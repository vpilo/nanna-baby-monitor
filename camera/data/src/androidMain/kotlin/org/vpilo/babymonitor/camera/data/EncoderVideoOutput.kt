package org.vpilo.babymonitor.camera.data

import android.annotation.SuppressLint
import androidx.camera.core.CameraInfo
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.ConstantObservable
import androidx.camera.core.impl.Observable
import androidx.camera.video.MediaSpec
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.StreamInfo
import androidx.camera.video.StreamInfoAccess
import androidx.camera.video.VideoCapabilities
import androidx.camera.video.VideoOutput
import androidx.camera.video.VideoSpec
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.common.Logger
import java.util.concurrent.Executor

@SuppressLint("RestrictedApi")
internal class EncoderVideoOutput(
    cameraResolution: CameraResolution,
    private val renderer: CameraGlRenderer,
    private val executor: Executor,
) : VideoOutput {
    private val mediaSpecObservable: Observable<MediaSpec> =
        ConstantObservable.withValue(
            MediaSpec
                .builder()
                .setVideoSpec(
                    VideoSpec
                        .builder()
                        .setQualitySelector(
                            QualitySelector.from(
                                when (cameraResolution) {
                                    CameraResolution.Low -> Quality.SD
                                    CameraResolution.Medium -> Quality.HD
                                    CameraResolution.High -> Quality.FHD
                                },
                            ),
                        ).build(),
                ).build(),
        )

    // Without this, CameraX leaves the video stream out of its repeating request and
    // the encoder's input surface never receives frames.
    private val sourceStreamRequiredObservable: Observable<Boolean> =
        ConstantObservable.withValue(true)

    private val streamInfoObservable: Observable<StreamInfo> =
        ConstantObservable.withValue(StreamInfoAccess.activeWith(0))

    override fun onSurfaceRequested(request: SurfaceRequest) {
        Logger.d(TAG) { "Got a surface request" }
        request.provideSurface(
            renderer.cameraInputSurface,
            executor,
        ) {
            Logger.d(TAG) { "Surface released" }
        }
    }

    override fun getMediaSpec(): Observable<MediaSpec> = mediaSpecObservable

    override fun getStreamInfo(): Observable<StreamInfo> = streamInfoObservable

    override fun isSourceStreamRequired(): Observable<Boolean> = sourceStreamRequiredObservable

    override fun getMediaCapabilities(
        cameraInfo: CameraInfo,
        sessionType: Int,
    ): VideoCapabilities = Recorder.getVideoCapabilities(cameraInfo)

    override fun onSourceStateChanged(state: VideoOutput.SourceState) {
        Logger.d(TAG) { "VideoOutput source state: $state" }
    }

    private companion object {
        private val TAG = EncoderVideoOutput::class
    }
}
