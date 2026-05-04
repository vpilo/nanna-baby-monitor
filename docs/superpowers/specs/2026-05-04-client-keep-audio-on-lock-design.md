# Client keeps audio + control connection alive when device locks

## Problem

When an Android client (Monitor) device screen locks while connected to a server with audio playing, the WebSockets eventually disconnect.
The user expects audio to keep playing and the control connection to remain established. Video, however, should not be transmitted while the
device is locked — decoding frames that no one will see is wasted CPU and battery.

The cause is that the client process has no Android foreground service. Once the activity stops, the OS throttles the process and eventually
breaks the WebSocket connections. The reconnection logic — currently a `SideEffect` in the `ClientHomeScreen` Composable — only fires when the
UI is composing, so it loops uselessly until the user unlocks again.

## Goal

Make the lock-screen transition invisible to the user from the audio/connectivity perspective:

- The control WebSocket stays open across lock; if it does drop, it reconnects on its own without UI involvement.
- The audio WebSocket stays open and audio keeps playing across lock.
- The video WebSocket closes when the camera-feed Composable leaves the resumed state, and reopens when it resumes (already true on main).
- The server's UI is unaffected — the server never sees the client disconnect on the control or audio channel.

## Non-goals

- No new UI on the client beyond the platform-mandated foreground-service notification.
- No changes to the server side beyond what's already in place.
- No changes to the user's persisted audio/video preferences (`Setting.ClientEnabledAudio`, `Setting.ClientEnabledVideo`).
- No changes to the video-stream pause/resume path. The `flatMapLatest`-driven `framesFlow` plus `LifecycleResumeEffect` already drop video
  subscribers (and therefore the video WebSocket) on lock and re-establish them on unlock, via `SharedResourceHolder`.

## Approach

Two parts:

1. Add a foreground service to the client role, role-aware so the foreground-service-type bitmask is correct for both server and client.
2. Move control-channel reconnect ownership from the UI into the network handler, with a new `ConnectionState.Reconnecting`.

### 1. Foreground service tied to client connection lifecycle

`DefaultNetworkClientRepository` instantiates a private `ForegroundServiceLink(AppRole.CLIENT)`.

- Call `start()` at the top of `connect(serverId)`, before opening any WebSocket. It is idempotent: re-entering `connect()` (e.g. via
  `reconnect()`) hits the registry's "already registered, log + return" path.
- Call `stop()` from `disconnect()`, after `closeAllConnections()`. Do **not** stop in `onControlConnectionClosed()` — that path now triggers
  handler-owned auto-reconnect, and stopping the foreground link there would let the OS throttle the process during the retry window.

### 2. Foreground service type derived from role at the call site

Android API 34+ requires `startForeground(id, notification, types)` with the corresponding runtime permissions. The current
`AndroidServiceHost` calls plain `startForeground(id, notification)`, which works only because the manifest declares `camera|microphone` and
the camera/microphone runtime permissions are granted in the server flow. The client device may not have those permissions in a Monitor-only
flow.

For the client, the relevant type is `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`. Its corresponding `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
permission is normal-level and granted automatically.

A given app instance has exactly one `AppRole`. Carry it across the module boundary and convert to Android constants at the
`startForeground` call site:

- `AndroidService` (interface) gains `val role: AppRole`.
- The anonymous `AndroidService` inside `ForegroundServiceLink.android.kt` returns the `role` constructor argument.
- `AndroidServiceRegistry`:
  - Rejects registration of a service whose `role` differs from the role of any already-registered service. Mixed roles are not legal in a
    single app run; treat as a programming error.
  - Exposes `currentRole: AppRole`, returning `AppRole.UNDECIDED` when nothing is registered.
- `AndroidServiceHost.onStartCommand` reads `currentRole` from the registry and converts it to the bitmask on the spot:
  - `AppRole.SERVER` → `FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE`.
  - `AppRole.CLIENT` → `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
  - `AppRole.UNDECIDED` is a programming error.
  - Calls `startForeground(NOTIFICATION_ID, createNotification(), types)` once. The role is fixed for the life of the app, so no runtime
    updates of the bitmask are needed.
- `androidService/src/androidMain/AndroidManifest.xml`:
  - Update `android:foregroundServiceType` to `camera|microphone|mediaPlayback` (the manifest is the superset; runtime selects from the
    role).
  - Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />`.

### 3. Control auto-reconnect with `Reconnecting` state

Pass `reconnect = true` to the control `WebSocketConnectionHandler` in `DefaultNetworkClientRepository.connect()`. The handler's existing
retry path (clean drop → `delay(Constants.RECONNECTION_TIMEOUT)` → `connect()`) becomes the recovery loop, mirroring how audio and video
already work.

Add `Reconnecting` to the `ConnectionState` sealed interface in `model/.../ConnectionState.kt`:

```kotlin
data class Reconnecting(val server: ServerId) : ConnectionState
```

Rework the disconnect callbacks in `DefaultNetworkClientRepository`:

- `onControlConnectionClosed(exception)` no longer calls `closeAllConnections()`. It only updates `connectionState` to
  `Reconnecting(serverId)`. Leaving `serverSelectionDataSource` set means the audio/video `SharedResourceHolder`-based receivers keep their
  handlers attached and ride out the gap on their own `reconnect = true`.
- `onControlConnectionOpened(server)` keeps current behaviour: state → `Connected(serverId)`.
- `disconnect()` keeps current behaviour: `closeAllConnections()` cancels the control handler (which sets `isDisconnectionHandled = true`,
  preventing further retry), clears server selection, state → `Disconnected(ClientQuit)`, then `foregroundLink.stop()`.

UI side:

- Remove the `SideEffect { if (state.connectionState is ConnectionState.Disconnected) viewModel.send(Reconnect) }` block in
  `ClientHomeScreen` — redundant now.
- The disconnected overlay (currently a single `is ConnectionState.Disconnected` check in `ClientHomeScreenContent`) needs a `Reconnecting`
  branch. Treat it like `Connecting` (same overlay, "Reconnecting…" copy).
- `ClientHomeScreenAction.Reconnect` and its VM handler become unused. Remove them.
- `NetworkClientRepository.reconnect()` (interface + impl) becomes unused — it only existed to back the UI action. Remove it.
- `ServerSelectionDataSource.lastServerId` is only read by `reconnect()`. Remove it; the data source becomes a thin holder of the current
  server `StateFlow`.

### 4. Cancel pending retry on explicit disconnect

`WebSocketConnectionHandler` schedules its retry as a fresh coroutine launched into its `coroutineScope` from
`invokeOnCompletion`. Today `disconnect()` only cancels the active `connectionJob`; a retry that's already in its `delay()` survives,
because `coroutineScope` itself is never cancelled. Once the control handler is on `reconnect = true`, this is a real bug: a user pressing
disconnect during the gap between drop and retry would see the connection silently come back.

Fix: track the retry coroutine in a `retryJob: Job?` and cancel it in `disconnect()`:

```kotlin
private var retryJob: Job? = null

// in invokeOnCompletion:
retryJob = coroutineScope.launch {
    onDisconnected(it ?: CancellationException("Unhandled closure"))
    if (reconnect) {
        delay(Constants.RECONNECTION_TIMEOUT)
        connect()
    }
}

fun disconnect() {
    isDisconnectionHandled = true
    connectionJob?.cancel()
    connectionJob = null
    retryJob?.cancel()
    retryJob = null
}
```

This also closes the same latent issue for audio and video, which already use `reconnect = true`.

### 5. No changes to audio playback or video pause/resume

Audio: `PlayReceivedAudioUseCase` runs in `vmScope` (= `viewModelScope`) and subscribes to `audioReceiverRepository.chunks`. With the
foreground service alive, the activity and ViewModel are not killed by the OS, so playback continues. `AudioTrack` already uses
`AudioAttributes.USAGE_MEDIA`, which is appropriate for media playback while the screen is off.

Video: `framesFlow` in `ClientHomeScreenViewModel` is a `flatMapLatest` over `videoReceiverRepository.decodedFrames`. `CameraFeed` collects
it in a `LifecycleResumeEffect`. On screen lock, the effect's `onPauseOrDispose` cancels the collection; the receiver's
`SharedResourceHolder` drops to zero subscribers and calls `stop()`, which closes the video WebSocket. On unlock, the effect restarts the
collection and the WebSocket reopens.

## Files touched

Already in place:

- `network/common/src/commonMain/kotlin/.../ForegroundServiceLink.kt` — shared `expect class` parameterised by `AppRole`.
- `network/common/src/desktopMain/kotlin/.../ForegroundServiceLink.desktop.kt` — no-op actual.
- `network/common/src/androidMain/kotlin/.../ForegroundServiceLink.android.kt` — registers an anonymous `AndroidService`.
- `network/server/src/commonMain/kotlin/.../DefaultNetworkServerRepository.kt` — already uses `ForegroundServiceLink(AppRole.SERVER)`.

To change in this work:

- `androidService/src/androidMain/AndroidManifest.xml` — add `mediaPlayback` to the service-type list, add `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  permission.
- `androidService/src/androidMain/kotlin/.../AndroidService.kt` — add `val role: AppRole`.
- `androidService/src/androidMain/kotlin/.../AndroidServiceRegistry.kt` — reject mixed-role registrations, expose `currentRole`.
- `androidService/src/androidMain/kotlin/.../AndroidServiceHost.kt` — read `currentRole` from the registry on `onStartCommand`, convert to
  the foreground-service-type bitmask, call `startForeground(id, notification, types)`.
- `network/common/src/androidMain/kotlin/.../ForegroundServiceLink.android.kt` — set the anonymous `AndroidService.role` from the `role`
  argument.
- `network/client/src/commonMain/kotlin/.../DefaultNetworkClientRepository.kt` — instantiate `ForegroundServiceLink(AppRole.CLIENT)`; start
  at top of `connect()`, stop at the end of `disconnect()`; pass `reconnect = true` to the control handler; rework
  `onControlConnectionClosed` to set `Reconnecting` without tearing down audio/video; remove `reconnect()` impl.
- `network/client/src/commonMain/kotlin/.../WebSocketConnectionHandler.kt` — track the retry coroutine in `retryJob` and cancel it in
  `disconnect()`.
- `network/client/src/commonMain/kotlin/.../ServerSelectionDataSource.kt` — remove `lastServerId`.
- `model/src/commonMain/kotlin/.../repository/NetworkClientRepository.kt` — remove `reconnect()` from the interface.
- `model/src/commonMain/kotlin/.../repository/ConnectionState.kt` — add `Reconnecting(server: ServerId)`.
- `appCommon/src/commonMain/kotlin/.../app/client/home/ClientHomeScreen.kt` — drop the `SideEffect` reconnect trigger; add a `Reconnecting`
  branch to the overlay.
- `appCommon/src/commonMain/kotlin/.../app/client/home/ClientHomeScreenAction.kt` — remove `Reconnect`.
- `appCommon/src/commonMain/kotlin/.../app/client/home/ClientHomeScreenViewModel.kt` — remove the `Reconnect` action handler.

## Open risks

- **Order of operations on `disconnect()`:** `foregroundLink.stop()` must run *after* `closeAllConnections()` so the OS doesn't kill the
  process mid-close.
- **One-shot retry semantics in `WebSocketConnectionHandler`:** `reconnect = true` only fires the retry path on a clean drop. If the retry's
  own `connect()` fails to establish (e.g. WiFi still down at retry time), the connect-failure branch sets `isDisconnectionHandled = true`
  and `invokeOnCompletion` skips further retry. Net effect: one retry per drop. For this work it is acceptable — the foreground service
  keeps the live socket warm via pings, so the common lock case doesn't hit retry at all; a clean blip → one retry covers most real
  outages; a deeper outage falls back to the existing disconnected overlay. Looping retry with backoff is a separate piece of work.
- **Manifest superset:** `camera|microphone|mediaPlayback` covers both roles. Runtime selects one based on `currentRole`.
- **Mixed-role guard:** relies on the invariant that an app instance has a single `AppRole`. If that ever stops being true, the guard
  surfaces the violation as a hard failure rather than misbehaving silently.
- **`connect()` called while app fully backgrounded:** `ForegroundServiceLink.start()` calls `Context.startForegroundService`, which is
  allowed when the app is in the foreground or has a recent user-initiated launch. The client's `connect()` runs from the VM in response to
  the user choosing a server, so the foreground requirement holds. If a future path calls `connect()` from the background, this assumption
  breaks.
