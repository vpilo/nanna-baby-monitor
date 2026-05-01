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
- No changes to the server side beyond what's already in place.
- No changes to the user's persisted audio/video preferences (`Setting.ClientEnabledAudio`, `Setting.ClientEnabledVideo`).

## Approach

The shared `ForegroundServiceLink` (already in `network/common`, parameterised by `AppRole`) gates the foreground affordance for both
roles. The client wires it up to its connection lifecycle. The video stream is dropped from the camera-feed Composable's
lifecycle so it pauses naturally when the camera feed is no longer on screen.

### 1. Foreground service tied to connection lifecycle

- Instantiate `ForegroundServiceLink(AppRole.CLIENT)` as a private member of `DefaultNetworkClientRepository`. Call `start()` at
  the beginning of `connect()`, before opening any WebSocket. Call `stop()` from `disconnect()` and from
  `onControlConnectionClosed()` after `closeAllConnections()` has run, so the service ends only when the client truly stops trying
  to maintain a session.
- Reconnect attempts (`reconnect()` and the auto-reconnect path triggered by the home screen) should not stop the service in
  between, since they call `connect()` again.

### 2. Foreground service type derived from role at the call site

Android API 34+ requires `startForeground(id, notification, types)` and the corresponding runtime permissions. The current
`AndroidServiceHost` calls plain `startForeground(id, notification)`, which works only because the manifest declares
`camera|microphone` and the camera/microphone runtime permissions are granted in the server flow.

For the client, the relevant type is `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`. Its corresponding `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
permission is normal-level and granted automatically. The client device may not have CAMERA/RECORD_AUDIO runtime permissions in a
Monitor-only flow, so the host must not assert `camera|microphone` types when the role is `CLIENT`.

A given app instance has exactly one `AppRole`. The design carries `AppRole` across the module boundary, and converts to Android constants
at the `startForeground` call site:

- `AndroidService` (interface) gains `val role: AppRole`.
- The anonymous `AndroidService` inside `ForegroundServiceLink.android.kt` returns the `role` constructor argument verbatim.
- `AndroidServiceRegistry`:
  - Rejects (logs + ignores, or `error("...")`) registration of a service whose `role` differs from the role of any already-
    registered service. Mixed roles are not legal in a single app run.
  - Exposes the current role via a getter (e.g. `currentRole: AppRole`), `AppRole.UNDECIDED` when nothing is registered.
- `AndroidServiceHost` reads the role on `onStartCommand` (via the registry getter, or via an extra placed on the starting intent
  by the registry — pick whichever is simpler in code; the registry getter is fine since the host only starts when the registry
  has at least one service registered) and converts it to the bitmask on the spot:
  - `AppRole.SERVER` → `FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE`.
  - `AppRole.CLIENT` → `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
  - `AppRole.UNDECIDED` is a programming error here; treat as such.
  - The host calls `startForeground(NOTIFICATION_ID, createNotification(), types)` once. No runtime updates of the bitmask are
    needed, since the role is fixed for the life of the app.
- `androidService/src/androidMain/AndroidManifest.xml`:
  - Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />`.
  - Update the `<service>` element's `android:foregroundServiceType` to `camera|microphone|mediaPlayback` (the manifest is the
    superset of types the service may request at runtime; the actual type at runtime is selected from the role).

### 3. Video stream paused/resumed by the camera-feed Composable

- `NetworkClientRepository` (and `DefaultNetworkClientRepository`) gain a separate axis for "is the video feed currently visible".
  Concrete shape:
  - Add `setVideoStreamPaused(paused: Boolean)` (or equivalent) to the repository.
  - The repository tracks two flags: `isVideoEnabledByUser` (already present, driven by `Setting.ClientEnabledVideo`) and
    `isVideoStreamPaused` (new, driven by Composable lifecycle).
  - Effective stream state = `isVideoEnabledByUser && !isVideoStreamPaused && currentAddress != null && captureMode != AUDIO_ONLY`.
    The existing `startVideoStream` / `stopVideoStream` are called when the effective state flips.
- `enableVideo(...)` and `setVideoStreamPaused(...)` both delegate to a single `reevaluateVideoStream()` private method to avoid
  duplicated guards.
- The Composable layer drives the new flag:
  - In `CameraFeed.kt`'s existing `LifecycleResumeEffect`, additionally call `viewModel.onVideoFeedResumed()` and on
    `onPauseOrDispose` call `viewModel.onVideoFeedPaused()`. (Or move the lifecycle effect into `ClientHomeScreen` if cleaner — the
    camera feed Composable already has a lifecycle effect for frame rendering, and consolidating is fine.)
  - `ClientHomeScreenViewModel` exposes `onVideoFeedResumed()` / `onVideoFeedPaused()` that call
    `networkClientRepository.setVideoStreamPaused(...)`.

### 4. No changes to audio playback

The existing `PlayReceivedAudioUseCase` runs in `vmScope` (= `viewModelScope`). With the foreground service alive, the activity and
ViewModel are not killed by the OS, so audio playback continues. The `AudioTrack` already uses `AudioAttributes.USAGE_MEDIA`, which
is appropriate for media playback while the screen is off.

## Files touched

Already in place (committed in `52f123f`):

- `network/common/src/commonMain/kotlin/.../ForegroundServiceLink.kt` — shared `expect class` parameterised by `AppRole`.
- `network/common/src/desktopMain/kotlin/.../ForegroundServiceLink.desktop.kt` — no-op actual.
- `network/common/src/androidMain/kotlin/.../ForegroundServiceLink.android.kt` — registers an anonymous `AndroidService`.
- `network/server/src/commonMain/kotlin/.../DefaultNetworkServerRepository.kt` — already uses `ForegroundServiceLink(AppRole.SERVER)`.

To change in this work:

- `androidService/src/androidMain/AndroidManifest.xml` — add `mediaPlayback` to the service type list, add
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission.
- `androidService/src/androidMain/kotlin/.../AndroidService.kt` — add `val role: AppRole`.
- `androidService/src/androidMain/kotlin/.../AndroidServiceRegistry.kt` — reject mixed-role registrations, expose `currentRole`.
- `androidService/src/androidMain/kotlin/.../AndroidServiceHost.kt` — read `currentRole` from the registry on `onStartCommand`,
  convert to the Android type bitmask on the spot, call `startForeground(id, notification, types)`.
- `network/common/src/androidMain/kotlin/.../ForegroundServiceLink.android.kt` — set the anonymous `AndroidService.role` from the
  `role` argument.
- `network/client/src/commonMain/kotlin/.../DefaultNetworkClientRepository.kt` — instantiate
  `ForegroundServiceLink(AppRole.CLIENT)`, start/stop on connect/disconnect, add `setVideoStreamPaused` and re-evaluation logic.
- `model/src/commonMain/kotlin/.../repository/NetworkClientRepository.kt` — expose `setVideoStreamPaused` (or chosen name).
- `appCommon/src/commonMain/kotlin/.../client/home/ClientHomeScreenViewModel.kt` — pass-through methods.
- `appCommon/src/commonMain/kotlin/.../client/home/CameraFeed.kt` (or `ClientHomeScreen.kt`) — call into the VM from the lifecycle
  effect.

## Open risks

- Order of operations on `disconnect()`: the link must stop *after* WebSockets close so the OS doesn't kill the process mid-close.
  Place `foregroundLink.stop()` at the end of the disconnect path, after `closeAllConnections()`.
- Manifest service-type list must be the superset of any runtime types used; `camera|microphone|mediaPlayback` covers both roles.
- The registry's mixed-role guard relies on the invariant that an app instance has a single `AppRole`. If that ever stops being
  true, the guard will surface the violation as a hard failure rather than silently misbehaving.
