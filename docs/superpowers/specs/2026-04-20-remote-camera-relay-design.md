# Remote Camera via Relay — Design

**Date:** 2026-04-20
**Branch:** relay-app

## Problem

The relay app currently assumes the camera device (server) is on the same LAN as the relay. It discovers cameras via mDNS and proxies outbound to them. This breaks when the camera is traveling: on 5G, on a hotel/business WiFi that blocks device-to-device traffic, or on a different network entirely.

## Goal

Allow the camera device to connect to the relay from anywhere in the world. A monitor device can then reach it through the relay regardless of network topology. Both devices only need internet access and the relay host address.

## Constraints

- Streaming is on-demand: the camera only encodes and transmits when a monitor is actively watching. 5G data is metered.
- Both use cases must coexist: local cameras (mDNS-discovered, relay proxies outbound) continue to work unchanged. If a camera is on the relay's LAN, the local path takes precedence.
- The camera connects to the relay automatically when `RelayHost` is configured; no separate toggle.
- No new user-facing settings.

---

## Endpoint Structure

### Unchanged
- `GET /relay/discovery` — streams newline-delimited server names to monitor clients

### Renamed (breaking change, both sides updated together)
| Old | New |
|-----|-----|
| `WS /relay/control/{serverId}` | `WS /relay/client/control/{serverId}` |
| `WS /relay/audio/{serverId}` | `WS /relay/client/audio/{serverId}` |
| `WS /relay/video/{serverId}` | `WS /relay/client/video/{serverId}` |

### New
- `WS /relay/server` — camera registration and signaling channel (persistent, low-bandwidth)
- `WS /relay/server/control/{serverId}` — camera connects here on demand
- `WS /relay/server/audio/{serverId}` — camera connects here on demand
- `WS /relay/server/video/{serverId}` — camera connects here on demand

All endpoints use the existing 256-byte binary handshake before any application data.

---

## Relay Changes (`network:relay`)

### Remote server registry

`DefaultNetworkRelayRepository` gains:

```kotlin
private val remoteServers = MutableStateFlow<Map<String, WebSocketServerSession>>(emptyMap())
```

Maps camera name → its live `/relay/server` session. The `/relay/discovery` endpoint combines `currentServers` (mDNS) and `remoteServers`, omitting any remote entry whose name already appears in mDNS (local takes precedence). The remote registration for such a camera is still maintained — it just isn't advertised while the local path is available.

### `/relay/server` endpoint

After handshake, relay reads camera name from first text frame, registers in `remoteServers`, then suspends consuming incoming frames (camera sends nothing; this just keeps the session alive and detects disconnection). On session close, removes from `remoteServers` and cancels any pending relays for that camera.

### Rendezvous mechanism

```kotlin
data class PendingRelay(
    val cameraArrived: CompletableDeferred<WebSocketServerSession> = CompletableDeferred(),
    val done: CompletableDeferred<Unit> = CompletableDeferred()
)

private val pendingRelays = ConcurrentHashMap<String, ArrayDeque<PendingRelay>>()
// key format: "$serverId:$stream"  e.g. "nursery:video"
```

### `/relay/client/{stream}/{serverId}` handler

- **Local camera** (serverId in `currentServers`): existing behaviour — relay connects outbound to `ws://ip:47812/{stream}`, runs `ProxySession`.
- **Remote camera** (serverId in `remoteServers`):
  1. Create `PendingRelay`, enqueue under `"$serverId:$stream"`
  2. Send `START_{STREAM}` text frame to camera's registration session
  3. Await `cameraArrived` with 10s timeout; close with error on timeout
  4. Call `ProxySession.run(client = this, server = cameraSession)`
  5. `finally`: complete `done`, send `STOP_{STREAM}` to camera, dequeue `PendingRelay`

### `/relay/server/{stream}/{serverId}` handler

After handshake:
1. Dequeue oldest `PendingRelay` for `"$serverId:$stream"`; close if none found
2. Complete `cameraArrived` with `this`
3. `await done` — keeps the Ktor handler and WS session alive until the proxy finishes

### Signaling messages (relay → camera, on `/relay/server`)

```
START_CONTROL  STOP_CONTROL
START_AUDIO    STOP_AUDIO
START_VIDEO    STOP_VIDEO
```

Multiple concurrent monitor clients each enqueue their own `PendingRelay` and each trigger one `START_*`, so the camera opens one connection per active client — consistent with how the local server handles multiple sessions.

---

## Camera Changes (`network:server`)

### New class: `RelayServerRegistration`

Mirrors `RelayDiscoverySource` on the client side. Lives in `network:server`.

**Responsibilities:**
- Connect to `wss://{relayHost}:47814/relay/server` via `relayHttpClient`
- Send handshake, then send device name as a text frame
- Loop reading signal frames; dispatch to stream job manager
- On `START_*`: launch a coroutine that opens `wss://{relayHost}:47814/relay/server/{stream}/{name}` and runs the corresponding streaming function inside that session
- On `STOP_*`: cancel the corresponding stream job
- On disconnection: cancel all stream jobs, retry after 5s

**Public API:**
```kotlin
fun updateRelayHost(host: String, deviceName: String, scope: CoroutineScope)
```
Passing an empty host stops registration and cancels all jobs.

### `DefaultNetworkServerRepository` changes

- Constructs a `RelayServerRegistration`
- Subscribes to `Setting.RelayHost` (same setting the monitor side already uses) and calls `updateRelayHost` on changes, passing the current device name
- Also re-calls `updateRelayHost` when the device name changes while relay is active

### Streaming function signatures

`videoStreamingServerWebSocket`, `audioStreamingServerWebSocket`, and `controlServerWebSocket` currently use `WebSocketServerSession` as receiver. Change receiver type to `WebSocketSession` (the common Ktor base interface). Both `WebSocketServerSession` (server-side) and `DefaultClientWebSocketSession` (client-side, used by relay stream connections) implement it. No logic changes required.

---

## Client Changes (`network:client`)

One line in `WebSocketConnectionHandler`:

```kotlin
// before
path = "/relay$endpointPath/${serverId.name}"
// after
path = "/relay/client$endpointPath/${serverId.name}"
```

`RelayDiscoverySource` is unchanged (`/relay/discovery` path unchanged).

---

## Settings & UI

`Setting.RelayHost` already exists in `AppSettings.kt` and is already shown in the settings screen. No new setting or UI element needed.

`CameraSelectionScreenViewModel` (or equivalent server-mode ViewModel) already propagates `RelayHost` changes to `networkClientRepository`. A parallel call to `networkServerRepository.setRelayHost()` is added in the same place.

---

## Data Flow Summary

```
Camera "nursery" (traveling, 5G)
  │  wss:// /relay/server
  └─→ Relay (home) ←─── /relay/discovery ─── Monitor "parent phone" (5G)
       │                                           │
       │  START_VIDEO signal                       │  wss:// /relay/client/video/nursery
       ↓                                           │
  Camera opens wss:// /relay/server/video/nursery ─┘
       └──── ProxySession pairs both sides ────────┘
```

Local camera path (unchanged):
```
Monitor ──wss──→ Relay ──ws──→ Camera (same LAN, mDNS-discovered)
```
