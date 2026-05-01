# Client keeps audio + control connection alive when device locks

## Problem

When an Android client (Monitor) device screen locks while connected to a server with audio playing, all WebSockets disconnect.
The user expects audio to keep playing and the control connection to remain established. Video, however, should not be transmitted while the device 
is locked — decoding frames that no one will see is wasted CPU and battery.

The cause is that the client process has no Android foreground service. Once the activity stops, the OS throttles the process and eventually
breaks the WebSocket connections. The reconnection logic loops uselessly until the user unlocks again.

## Goal

Make the lock-screen transition invisible to the user from the audio/connectivity perspective:

- The control WebSocket stays open across lock.
- The audio WebSocket stays open and audio keeps playing across lock.
- The video WebSocket closes when the camera-feed Composable leaves the resumed state, and reopens when it resumes.
- The server's UI is unaffected — the server never sees the client disconnect on the control or audio channel.

## Non-goals

- No new UI on the client beyond the platform-mandated foreground-service notification.
- No changes to the server side.
- No changes to the user's persisted audio/video preferences (`Setting.ClientEnabledAudio`, `Setting.ClientEnabledVideo`).

## Approach

Use the server's existing `ForegroundServiceLink` also on the client, tied to the client's connection 
lifecycle. Drop the video stream from the Composable lifecycle so it pauses naturally when the camera feed is no longer on screen.

### 1. Foreground service tied to connection lifecycle

- Instantiate the `ForegroundServiceLink` as a private member of `DefaultNetworkClientRepository`. Call `start()` at the beginning of `connect()`,
- before opening any WebSocket. Call `stop()` from `disconnect()` and from `onControlConnectionClosed()` after `closeAllConnections()` 
  has run, so the service ends only when the client truly stops trying to maintain a session.
- Reconnect attempts (`reconnect()` and the auto-reconnect path triggered by the home screen) should not stop the service in between, since 
  they call `connect()` again.

### 2. Foreground service types declared and applied per registered service

Android API 34+ requires `startForeground(id, notification, types)` and runtime permissions for each `FOREGROUND_SERVICE_TYPE_*` declared. The current `AndroidServiceHost` calls plain `startForeground(id, notification)`, which works only because the manifest declares `camera|microphone` and the camera/microphone runtime permissions are granted in the server flow.

For the client, the relevant type is `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`, whose corresponding `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission is normal-level and granted automatically. The client device will not have CAMERA/RECORD_AUDIO runtime permissions in a Monitor-only flow, so the host must not assert `camera|microphone` types when only the client service is registered.

Changes:

- Extend the `AndroidService` interface with a `foregroundServiceTypes: Int` property (bitmask of `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` constants).
- `ServerForegroundServiceLink.android` returns `FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE`.
- `ClientForegroundServiceLink.android` returns `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
- `AndroidServiceRegistry` exposes the union of `foregroundServiceTypes` across registered services and notifies `AndroidServiceHost` when it changes (e.g. when a service registers/unregisters while the host is already running).
- `AndroidServiceHost.onStartCommand` calls `startForeground(NOTIFICATION_ID, createNotification(), registry.activeForegroundServiceTypes)`. When the active type set changes mid-lifetime (rare, but covers a future case where, say, the client registers while the server is also running), the host re-invokes `startForeground` with the updated bitmask.
- `androidService/src/androidMain/AndroidManifest.xml`:
  - Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />`.
  - Update the `<service>` element's `android:foregroundServiceType` to `camera|microphone|mediaPlayback`.

### 3. Video stream paused/resumed by the camera-feed Composable

- `NetworkClientRepository` (and `DefaultNetworkClientRepository`) gain a separate axis for "is the video feed currently visible". Concrete shape:
  - Add `setVideoStreamPaused(paused: Boolean)` (or equivalent) to the repository.
  - The repository tracks two flags: `isVideoEnabledByUser` (already present, driven by `Setting.ClientEnabledVideo`) and `isVideoStreamPaused` (new, driven by Composable lifecycle).
  - Effective stream state = `isVideoEnabledByUser && !isVideoStreamPaused && currentAddress != null && captureMode != AUDIO_ONLY`. The existing `startVideoStream` / `stopVideoStream` are called when the effective state flips.
- `enableVideo(...)` and `setVideoStreamPaused(...)` both delegate to a single `reevaluateVideoStream()` private method to avoid duplicated guards.
- The Composable layer drives the new flag:
  - In `CameraFeed.kt`'s existing `LifecycleResumeEffect`, additionally call `viewModel.onVideoFeedResumed()` and on `onPauseOrDispose` call `viewModel.onVideoFeedPaused()`. (Or move the lifecycle effect into `ClientHomeScreen` if cleaner — the camera feed Composable already has a lifecycle effect for frame rendering, and consolidating is fine.)
  - `ClientHomeScreenViewModel` exposes `onVideoFeedResumed()` / `onVideoFeedPaused()` that call `networkClientRepository.setVideoStreamPaused(...)`.

### 4. No changes to audio playback

The existing `PlayReceivedAudioUseCase` runs in `vmScope` (= `viewModelScope`). With the foreground service alive, the activity and ViewModel are not killed by the OS, so audio playback continues. The `AudioTrack` already uses `AudioAttributes.USAGE_MEDIA` which is appropriate for media playback while screen is off.

## Files touched

- `androidService/src/androidMain/AndroidManifest.xml` — add `mediaPlayback` to the service type list, add `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission.
- `androidService/src/androidMain/kotlin/.../AndroidService.kt` — add `foregroundServiceTypes: Int`.
- `androidService/src/androidMain/kotlin/.../AndroidServiceRegistry.kt` — track and expose the active type bitmask.
- `androidService/src/androidMain/kotlin/.../AndroidServiceHost.kt` — pass the active types to `startForeground`, refresh on changes.
- `network/server/src/androidMain/kotlin/.../ServerForegroundServiceLink.android.kt` — declare `camera|microphone` types.
- `network/client/src/commonMain/kotlin/.../ClientForegroundServiceLink.kt` — new `expect class`.
- `network/client/src/desktopMain/kotlin/.../ClientForegroundServiceLink.desktop.kt` — no-op actual.
- `network/client/src/androidMain/kotlin/.../ClientForegroundServiceLink.android.kt` — registers with the registry, declares `mediaPlayback`.
- `network/client/src/commonMain/kotlin/.../DefaultNetworkClientRepository.kt` — instantiate the link, start/stop on connect/disconnect, add `setVideoStreamPaused` and re-evaluation logic.
- `model/src/commonMain/kotlin/.../repository/NetworkClientRepository.kt` — expose `setVideoStreamPaused` (or chosen name).
- `appCommon/src/commonMain/kotlin/.../client/home/ClientHomeScreenViewModel.kt` — pass-through methods.
- `appCommon/src/commonMain/kotlin/.../client/home/CameraFeed.kt` (or `ClientHomeScreen.kt`) — call into VM from the lifecycle effect.

## Open risks

- Order of operations on `disconnect()`: the link must stop *after* WebSockets close so the OS doesn't kill the process mid-close. Place `foregroundLink.stop()` at the end of `closeAllConnections()`'s caller, after the disconnect paths complete.
- Re-invoking `startForeground` to update the type bitmask is supported by Android, but only while the service is in the foreground state. If types change while the service is not running, the next `onStartCommand` will use the up-to-date union. The registry should call back into the host only if the host is started.
- Manifest service-type list must be the superset of any runtime types used; this is satisfied by `camera|microphone|mediaPlayback`.
