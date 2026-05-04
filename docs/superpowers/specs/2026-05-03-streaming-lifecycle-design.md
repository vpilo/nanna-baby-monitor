# Client Streaming Lifecycle — Design

**Date:** 2026-05-03
**Branch:** streaming-lifecycle

## Problem

On the client, the audio and video decoding pipelines are subscriber-driven, but the WebSockets that feed them are imperatively controlled by `DefaultNetworkClientRepository`. This decouples the WebSocket lifetime from actual consumption.

Concrete symptom (video): when `CameraFeed` detaches (`LifecycleResumeEffect.onPauseOrDispose`) the only subscriber on `decodedFrames` goes away and the decoder stops via `SharedResourceHolder`. The video WebSocket, however, stays open because it is governed by `enableVideo(...)` and `serverState.captureMode`, not by subscriber count. The server keeps encoding and transmitting; frames cycle through `NetworkVideoDataSource.frames` (a `MutableSharedFlow` with `DROP_OLDEST`) and are dropped client-side. Bandwidth and server-side encoding are wasted.

Audio has the same structure but does not exhibit the leak in practice: the only consumer of `chunks` is `PlayReceivedAudioUseCase`, whose `playbackJob` is bound to `vmScope` and toggled by user intent — its lifetime already tracks the WebSocket's.

## Goal

Couple each medium's WebSocket lifetime to the lifetime of its actual consumer:

- **Video:** `CameraFeed` (visible & resumed) ⇄ `decodedFrames` subscriber ⇄ video WebSocket.
- **Audio:** `PlayReceivedAudioUseCase` collecting `chunks` ⇄ subscriber ⇄ audio WebSocket.

`DefaultNetworkClientRepository` is freed from owning media-stream lifecycle. It owns only the control connection, server discovery, and exposing the connection target.

## Non-goals

- Changing the WebSocket protocol or session functions (`videoStreamingClientWebSocket`, `audioStreamingClientWebSocket`) — they are reused as-is.
- Changing the host-fallback / reconnect policy in `WebSocketConnectionHandler` — it is reused.
- Changing the decoder, the data sources, or the `SharedResourceHolder` reactor mechanics.

---

## Architecture

### `ConnectionTargetDataSource` (new)

A small Koin singleton in `network/client`, sibling to `NetworkControlDataSource`. Holds the address and `ServerId` together — they are always set/cleared as a pair. Exposes the value as a `StateFlow<ConnectionTarget?>`.

```kotlin
internal data class ConnectionTarget(
    val address: InetAddress,
    val serverId: ServerId,
)

internal class ConnectionTargetDataSource {
    private val _target = MutableStateFlow<ConnectionTarget?>(null)
    val target: StateFlow<ConnectionTarget?> = _target.asStateFlow()
    fun set(target: ConnectionTarget?) { _target.value = target }
}
```

Writers: `DefaultNetworkClientRepository`. Readers: `NetworkVideoReceiverRepository`, `NetworkAudioReceiverRepository`.

### Receiver repositories own the WebSocket session

`NetworkVideoReceiverRepository` and `NetworkAudioReceiverRepository` continue to extend `SharedResourceHolder<T>`. Their `start()` / `stop()` grow to manage a `WebSocketConnectionHandler` for the medium's stream endpoint.

Sketch (video; audio symmetric):

```kotlin
internal class NetworkVideoReceiverRepository(
    dataSource: NetworkVideoDataSource,
    private val connectionTargetDataSource: ConnectionTargetDataSource,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<ImageBitmap>(...), StreamingVideoReceiverRepository {

    override val decodedFrames: SharedFlow<ImageBitmap> = collector.asSharedFlow()
    private val decoder = VideoDecoder(dataSource.frames, collector, coroutineContext)

    private var handler: WebSocketConnectionHandler? = null
    private var targetJob: Job? = null

    override fun start() {
        decoder.start()
        targetJob = coroutineScope.launch {
            connectionTargetDataSource.target.collect { target ->
                handler?.disconnect()
                handler = null
                if (target == null) return@collect
                handler = WebSocketConnectionHandler(
                    hosts = setOf(target.address),
                    endpointPath = Endpoints.STREAM_VIDEO,
                    serverId = target.serverId,
                    sessionBlock = { _ -> videoStreamingClientWebSocket() },
                    onDisconnected = {
                        Logger.i(TAG) { "Video disconnected, reconnecting" }
                        delay(Constants.RECONNECTION_TIMEOUT)
                        handler?.connect()
                    },
                    coroutineScope = coroutineScope,
                ).apply { connect() }
            }
        }
    }

    override fun stop() {
        handler?.disconnect()
        handler = null
        targetJob?.cancel()
        targetJob = null
        decoder.stop()
    }
}
```

Properties:

- `start()` runs when the first `decodedFrames` subscriber appears (via `SharedResourceHolder.reactor`); `stop()` when the last one leaves.
- The handler is recreated whenever `target` changes value (including `null` ↔ non-null).
- The trailing reconnect inside `onDisconnected` is a no-op after `stop()` because `handler` has already been nulled, matching the existing pattern in `DefaultNetworkClientRepository`.
- The dedicated session function (`videoStreamingClientWebSocket()`) is unchanged; it continues to look up its `NetworkVideoDataSource` from Koin.

`NetworkAudioReceiverRepository` mirrors the same shape using `Endpoints.STREAM_AUDIO` and `audioStreamingClientWebSocket()`.

### `SharedResourceHolder.isActive` becomes a `Flow<Boolean>`

Replace the current `val isActive: Boolean` with:

```kotlin
val isActive: Flow<Boolean> =
    collector.subscriptionCount
        .map { it > 0 }
        .distinctUntilChanged()
```

Add `val isActive: Flow<Boolean>` to `StreamingVideoReceiverRepository` and `StreamingAudioReceiverRepository` interfaces (in `model/`). Both implementations satisfy the contract from the base class. Existing callers of the old `Boolean` property migrate to `.first()` or collection.

### `DefaultNetworkClientRepository` shrinks

Removed:

- `startAudioStream()` / `stopAudioStream()` / `startVideoStream()` / `stopVideoStream()`.
- `audioHandler` / `videoHandler` fields.
- `isAudioEnabled` / `isVideoEnabled` fields and the `enableAudio` / `enableVideo` overrides.
- The `init { … networkControlDataSource.serverState.collect { … } }` reactor that imperatively stopped the wrong stream on `captureMode` change.
- The `currentAddress` field (moved to `ConnectionTargetDataSource`).
- The two `if (isXxxEnabled) startXxxStream()` calls in `onControlConnectionOpened`.

Retained:

- `connect` / `reconnect` / `disconnect` / `setRelayHost` / `setDeviceName`.
- The control `WebSocketConnectionHandler`.
- `connectionStateFlow`, `serverStateFlow`, `discoveredServerIdsFlow`.

`onControlConnectionOpened(server, address)` writes `connectionTargetDataSource.set(ConnectionTarget(address, server))`. `closeAllConnections()` and `onControlConnectionClosed(...)` write `connectionTargetDataSource.set(null)`.

### `NetworkClientRepository` interface

Remove `enableAudio(Boolean)` and `enableVideo(Boolean)`. The interface is left with control-connection lifecycle and observation flows only.

### Removed: client-only `isStreamingAudio` / `isStreamingVideo`

These were client-internal flags used to keep VM state consistent with `DefaultNetworkClientRepository`'s imperative WebSocket control. They are not on the wire. After the refactor the receiver repositories' `isActive` flows replace them.

Removed:

- `NetworkControlDataSource.setIsStreamingAudio(...)` / `setIsStreamingVideo(...)`.
- `ServerState.isStreamingAudio` / `ServerState.isStreamingVideo` fields.
- All call sites of the setters (previously inside the session blocks and on-disconnect callbacks of `DefaultNetworkClientRepository`).

The receiver repositories do **not** call any "I'm streaming" notifier inside their session blocks.

---

## VM — `ClientHomeScreenViewModel`

### Constructor

Gains `audioReceiverRepository: StreamingAudioReceiverRepository` alongside the existing video one.

### Video frames are gated in the VM

Replace `val frames = videoReceiverRepository.decodedFrames` with a gated flow. The boolean is hoisted as a private field so it can also drive UI state:

```kotlin
private val videoGate: Flow<Boolean> =
    combine(
        settingsRepository.flowOf(Setting.ClientEnabledVideo),
        networkClientRepository.serverStateFlow,
    ) { enabled, server ->
        enabled && server.isAvailable && server.captureMode != CaptureMode.AUDIO_ONLY
    }.distinctUntilChanged()

val frames: Flow<ImageBitmap> =
    videoGate.flatMapLatest { open ->
        if (!open) return@flatMapLatest emptyFlow()
        videoReceiverRepository.decodedFrames
    }
```

When the gate closes, `flatMapLatest` cancels the inner subscription on `decodedFrames` → `SharedResourceHolder` count drops → receiver repo's `stop()` runs → WebSocket closes.

### Audio playback is driven by a derived gate

Replace today's `if (!isPlaying && serverState.isStreamingAudio) toggle` rule (now circular) with a setting/mode-derived gate:

```kotlin
private val shouldPlayAudio: Flow<Boolean> =
    combine(
        settingsRepository.flowOf(Setting.ClientEnabledAudio),
        networkClientRepository.serverStateFlow,
    ) { enabled, server ->
        enabled && server.isAvailable && server.captureMode != CaptureMode.VIDEO_ONLY
    }.distinctUntilChanged()
```

Inside `onSubscribed()`:

```kotlin
shouldPlayAudio.subscribe { should ->
    if (should == playReceivedAudio.isPlaying.value) return@subscribe
    playReceivedAudio.toggle(vmScope)
}
```

This subsumes both the old auto-start rule and the old auto-stop-on-`VIDEO_ONLY` rule.

### State derivation

```kotlin
combine(
    audioReceiverRepository.isActive,
    videoReceiverRepository.isActive,
    networkClientRepository.serverStateFlow,
) { isAudioPlaying, isVideoPlaying, serverState ->
    state.copy(
        captureMode = serverState.captureMode,
        batteryLevel = serverState.batteryLevel,
        signalQuality = serverState.signalQuality,
        isAudioPlaying = isAudioPlaying,
        isVideoPlaying = isVideoPlaying,
    ).update()
}.collectLatest()
```

`serverState.isStreamingAudio` / `isStreamingVideo` are no longer read.

### Action handlers

```kotlin
ClientHomeScreenAction.ToggleAudio ->
    settingsRepository.save(Setting.ClientEnabledAudio, !state.isAudioPlaying)
ClientHomeScreenAction.ToggleVideo ->
    settingsRepository.save(Setting.ClientEnabledVideo, !state.isVideoPlaying)
```

The setting flip is now the load-bearing path, so it must be immediate. `saveDelayed` would have introduced a 2-second click latency. This requires `SettingsRepository.save(...)` (immediate) to exist alongside `saveDelayed(...)`.

### Composable side

`CameraFeed` is unchanged. It continues to collect `viewModel.frames` only while resumed, via `LifecycleResumeEffect`. The VM's gate determines whether the underlying flow has any emissions at all; the lifecycle determines whether the collection job exists. Both must be true for there to be a subscriber on `decodedFrames`.

---

## Behavior summary

| Scenario | Video WS | Audio WS |
|---|---|---|
| Screen visible, both toggled on, server `AUDIO_AND_VIDEO` | open | open |
| Screen visible, video toggled off | closed | open if audio toggled on |
| Screen visible, server `VIDEO_ONLY` | open if video toggled on | closed |
| Screen visible, server `AUDIO_ONLY` | closed | open if audio toggled on |
| Screen paused (lifecycle PAUSED) — `CameraFeed` not collecting | closed | open if audio toggled on |
| Screen disposed (`onUnsubscribed` → `disconnect()`) | closed | closed |
| Control connection dropped (target → null) | closed | closed |
| Control reconnected (target → non-null) | reopens if subscribers present | reopens if playback active |

Audio surviving screen-pause is intentional: the playback `playbackJob` runs in `vmScope`, which is not bound to the Compose lifecycle.

## Reconnect & control-connection flow

- `connect(server)` → `closeAllConnections()` (sets target null, receiver repos disconnect their handlers if any) → opens fresh control handler. On success: target is set, receiver repos open their handlers if subscribers present.
- `reconnect()` reuses `connect(last)`; nothing new.
- Control disconnect (any path) clears the target. Receiver repos see the change via `target.collect` and tear down their handlers. They stay alive (subscribers still present), waiting for a new target.

## Buffer behavior across sessions

`NetworkVideoDataSource` and `NetworkAudioDataSource` remain Koin singletons. With `replay = 0` on their `MutableSharedFlow`, a subscriber in a new session sees only future emissions. No stale chunks survive across reconnects.

## Server-facing flag flap

With subscriber-driven sessions, rapid resume/pause cycles or fast capture-mode flips will flap the streaming WebSocket open/closed (and therefore briefly stress the server's per-medium connect/disconnect path). Today this did not happen because the WebSocket was tied to a sticky setting. Ship without debounce; revisit if observable in practice.

## Koin module changes

`networkClientKoinModule`:

- `singleOf(::ConnectionTargetDataSource)`
- `NetworkVideoReceiverRepository` and `NetworkAudioReceiverRepository` constructors gain `ConnectionTargetDataSource`.

`appCommonKoinModule`:

- `ClientHomeScreenViewModel` constructor gains `StreamingAudioReceiverRepository`.

## Pre-requisite work

- Add `SettingsRepository.save(setting, value)` (immediate write) if not already present alongside `saveDelayed`. The `ToggleAudio` / `ToggleVideo` actions depend on it.

## Risks and open questions

- **Flag flap.** Captured above; accepted, monitor in practice.
