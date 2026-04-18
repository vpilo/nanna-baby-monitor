# Remote Relay — Design Spec

**Date:** 2026-04-18  
**Status:** Approved

## Overview

Add remote connectivity to the Baby Monitor app so a client (monitor) can reach a server (camera) over the internet when both devices are not on the same LAN. A self-hosted relay application runs on a Raspberry Pi (or similar), is publicly reachable via DDNS + router port forwarding, and acts as a transparent WebSocket proxy between the remote client and the local camera server.

---

## Scope and constraints

- The relay is a JVM application to reuse the existing JmDNS-based mDNS discovery.
- One relay per deployment. No support for multiple relays on the client side.
- The relay supports multiple local servers (cameras) on the same LAN simultaneously, identified by `ServerId`.
- TLS is required end-to-end (client ↔ relay). The relay ↔ local server leg is plain `ws://` (LAN traffic).
- Certificate strategy: pre-generated self-signed cert bundled at compile time. To be replaced with proper pairing before any public release.
- No changes to the existing LAN discovery or connection flow.

---

## New modules

### `network:relay`

Relay library. JVM target only (`jvm("desktop")`).

**Dependencies:** `network:common` (reuses `DiscoveryManager.desktop.kt`, `Constants`, `Endpoints`, `ServerId`).

**Key classes:**
- `RelayConfig` — `data class`: `port: Int`, `secret: ByteArray` (256 bytes derived from password)
- `DefaultNetworkRelayRepository` — owns the Ktor TLS server and `DiscoveryManager`; starts/stops both
- `ProxySession` — `suspend fun run(client: WebSocketSession, server: WebSocketSession)`: two coroutines shuttling frames bidirectionally; closes both sides when either drops
- `RelayHandshake` — server-side handshake logic (see Auth section)

### `appRelay`

Thin JVM entry point. Password and keystore are hardcoded (both will be replaced by proper pairing before publication). Wires Koin, starts `DefaultNetworkRelayRepository`. No UI, no configuration required — the relay is ready to run with no arguments.

### Constants (`network:common`)

New constant: `RELAY_PORT = 47814`.

---

## TLS and certificate strategy

A self-signed RSA-4096 certificate is generated once at development time with a 10-year validity period.

| Artifact | Location | Used by |
|---|---|---|
| PKCS12 keystore (`relay.p12`) | `appRelay/src/main/resources/` | Relay TLS server |
| Public cert (`relay.crt`) | `network:client/src/commonMain/resources/` | Client cert pinning |

The relay serves `wss://` only — no plain `ws://` fallback on port 47814.

The client uses a custom `X509TrustManager` (expect/actual: JVM and Android load keystores differently) that pins `relay.crt` and rejects any relay presenting a different certificate.

---

## Auth handshake protocol

Applied to every new WebSocket connection on the relay (discovery, audio, video, control).

**Secret derivation** (both sides, same algorithm):
```
secret = SHA-256(password.toByteArray(UTF-8)) repeated 8 times  →  256 bytes
```
`deriveSecret(password: String): ByteArray` lives in `network:common` so both `network:relay` and `network:client` can share it without a circular dependency.

**Relay-side flow:**
```
connection opened
→ withTimeout(2_000 ms):
    read one Frame
    if timeout              → close, no response
    if frame.data.size ≠ 256 → close immediately, do not inspect content
    if bytes ≠ secret       → close, no response
    else                    → proceed
```

**Client-side:** send the 256 bytes as a single binary frame immediately on connection open, before any other data.

**Security properties (with TLS):**
- Secret travels encrypted — no replay risk from passive observation
- Cert pinning prevents MITM / relay impersonation
- Wrong-size fast-close leaks no timing information about secret content
- No oracle: all rejection paths are silent closes

**Remaining limitation:** source repo is private; cert and secret are compiled in. Must be replaced with per-deployment key generation before any public release.

---

## Relay endpoints

All `wss://`, all require handshake before any other data.

| Path | Purpose |
|---|---|
| `GET /relay/discovery` (WS) | Returns list of currently-discovered local servers; pushes updates on mDNS change |
| `WS /relay/audio/{serverId}` | Proxies to `ws://{localIp}:47812/audio` |
| `WS /relay/video/{serverId}` | Proxies to `ws://{localIp}:47812/video` |
| `WS /relay/control/{serverId}` | Proxies to `ws://{localIp}:47812/control` |

**Discovery endpoint:**
1. Handshake validated
2. Send current server list as a text frame: newline-delimited `ServerId` strings
3. Keep connection open; push a new frame whenever mDNS state changes (server appears or disappears)

**Proxy endpoint flow:**
1. Handshake validated
2. Look up `{serverId}` in `DiscoveryManager` state; if not found → send error text frame, close
3. Open plain `ws://` connection to the local server; if it fails → send error text frame, close
4. `ProxySession.run()` — bidirectional byte forwarding until either side closes
5. On close: close both sides, release resources

The relay is stateless across connections. No session tokens. No memory of which server a client selected during discovery — each proxy connection carries `{serverId}` in its path.

---

## Client app changes

### Settings (`appCommon`)

- Add `relayHost: String` (empty string = no relay configured) to `AppSettings` in `appCommon`
- New text field in the settings UI: "Remote relay hostname" (e.g. `myhome.dyndns.com`). Port is fixed at `47814`, not user-configurable.
- `ClientHomeViewModel` observes `relayHost` and pushes updates to `DefaultNetworkClientRepository` via a setter/flow whenever the value changes.

### `DiscoveredServer` — unchanged

For relay-discovered servers, `addresses` contains a single `InetAddress` built from the relay hostname (`InetAddress.getByName(relayHost)`). No model changes required.

### Discovery (`network:client`)

`DefaultNetworkClientRepository` runs two discovery sources concurrently:

- **LAN:** existing `DiscoveryManager` — unchanged
- **Relay:** new `RelayDiscoverySource` — connects to `wss://{relayHost}:47814/relay/discovery`, performs handshake, listens for server-list frames, emits `List<DiscoveredServer>` on each update

Both sources are exposed as separate flows:

```kotlin
val localServers: StateFlow<List<DiscoveredServer>>
val relayServers: StateFlow<List<DiscoveredServer>>
```

If `relayHost` is empty, `RelayDiscoverySource` emits nothing. The UI presents the two lists in distinct sections so the user can tell LAN from relay servers at a glance.

### Connection (`network:client`)

`DefaultNetworkClientRepository.connect(server: DiscoveredServer)` resolves the connection type by checking which list contains the server:

- Found in `localServers` → `ConnectionHandler` (plain `ws://`, port `47812`)
- Found only in `relayServers` → `RelayConnectionHandler` (`wss://`, port `47814`, handshake, `{server.id}` in path)
- Found in both (server visible on LAN and relay simultaneously) → prefer `Local` to avoid the unnecessary relay hop

`RelayConnectionHandler` opens `wss://{relayHost}:47814/relay/{endpoint}/{server.id}`, performs the handshake, then hands the `WebSocketSession` to the existing WebSocket handler functions unchanged.

The existing audio/video/control WebSocket handlers (`videoStreamingClientWebSocket()`, etc.) receive a session and operate on frames — they have no knowledge of whether the session is LAN or relay.

---

## Data flow summary

```
Remote client app
  │  wss:// + cert pinning + 256-byte handshake
  ▼
Relay (Raspberry Pi, port 47814)
  │  plain ws:// (LAN)
  ▼
Local server app (port 47812)
```

Discovery path:
```
Remote client  →  wss://.../relay/discovery  →  Relay reads DiscoveryManager state
```

Stream path (one per audio/video/control):
```
Remote client  →  wss://.../relay/audio/{id}  →  ProxySession  →  ws://.../audio
```

---

## Out of scope

- Kotlin/Native relay target (noted as future option for Pi Zero deployments)
- Multiple relays per client
- Per-deployment key generation / proper pairing UI (required before public release)
- DDNS configuration (user responsibility)
- Router port forwarding (user responsibility)
- Rate limiting / DoS protection on the relay (future hardening)
