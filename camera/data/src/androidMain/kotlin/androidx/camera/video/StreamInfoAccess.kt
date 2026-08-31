@file:Suppress("RestrictedApi")

package androidx.camera.video

/**
 * Bridges the package-private [StreamInfo.of] factory and [StreamInfo.StreamState] enum
 * so [org.vpilo.babymonitor.camera.data.VideoCaptureDataSource]'s custom `VideoOutput`
 * can construct fresh `StreamInfo` instances.
 *
 * CameraX exposes [VideoOutput.getStreamInfo] as a `LIBRARY` API but keeps the
 * factory members hidden. The default [StreamInfo.ALWAYS_ACTIVE_OBSERVABLE] is a
 * `ConstantObservable` that fires its observer only on subscribe, so once
 * `VideoCapture.clearPipeline()` resets the cached `StreamInfo` to INACTIVE
 * (which happens whenever a `SurfaceRequest.invalidate()` causes a surface-setup
 * error during the rebind), there is no further emission to push it back to ACTIVE
 * and the encoder surface is added as non-repeating - the camera then never drives
 * frames to it.
 *
 * Work around this by handing CameraX a `MutableStateObservable<StreamInfo>` and
 * re-emitting a new active `StreamInfo` from each `onSurfaceRequested`. This
 * forces `VideoCapture` to re-fire and re-apply the active state.
 */
internal object StreamInfoAccess {
    fun activeWith(streamId: Int): StreamInfo = StreamInfo.of(streamId, StreamInfo.StreamState.ACTIVE)
}
