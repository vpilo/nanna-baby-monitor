# Network protocol version

## Goal

- Every camera↔monitor connection and every relay registration connection carries a protocol version.
- A connection failing because of a version mismatch is reported to the user, saying which side needs updating.
- No backward compatibility: builds without the version are simply incompatible. The app is unreleased; all installs get updated.

## Versions

- `PEER_PROTOCOL_VERSION = 1` in `network:internal`, shared by `network:server` and `network:client`.
- `RELAY_PROTOCOL_VERSION = 1` in `network:security`, shared by the apps and `appRelay`.
- Integers, compared for strict equality. Bumped only on wire-incompatible changes; unrelated to the app version and to the mDNS
  `DEVICE_ATTRIBUTE_SCHEMA_VERSION`.
- Independent: a relay protocol change doesn't force camera/monitor updates, and vice versa.

## Wire protocol

- The connector sends one text frame holding the decimal version as its first frame.
- The acceptor reads it:
  - Equal: continues with the existing protocol, no reply.
  - Different: closes with custom close code `4000` and its own version, in decimal, as the close message.
  - Malformed or non-text: closes with `PROTOCOL_ERROR`.
- The connector learns the mismatch from the close when its next receive fails, and compares the versions:
  - remote > local: this device is outdated.
  - remote < local: the other side is outdated.
- Not bound into any handshake transcript: with strict equality and no negotiation there is nothing to downgrade. A spoofed mismatch
  only denies service, which an interceptor can do anyway.

### Helpers (`network:internal`, `protocol/ProtocolVersion.kt`)

- `WebSocketSession.sendProtocolVersion(version: Int)`
- `WebSocketSession.acceptProtocolVersion(version: Int): Boolean` - closes as above and returns `false` on mismatch or malformed input.
- `ProtocolVersionMismatchException(remoteVersion: Int)` and a helper turning a `CloseReason` with code `4000` into it, so every
  connector maps the close the same way.

## Peer checkpoints

The version is the monitor's first frame on every peer connection, local or relayed (the relay proxies it end to end):

| Endpoint                         | Connector (monitor)                             | Acceptor (camera)                                             |
|----------------------------------|-------------------------------------------------|---------------------------------------------------------------|
| `/control`, `/audio`, `/video`   | `clientSessionHandshake`, before the request    | `serverSessionHandshake`, before reading the request          |
| `/pair`                          | `DefaultClientPairingRepository.runPairing`, before the hello | `PairingCoordinator.handlePairingSession`, before the pairing window check, so a closed window doesn't mask a mismatch |

## Relay checkpoints

- Only the registration connections are relay-versioned: camera → `SERVER_REGISTRATION`, monitor → `CLIENT_DISCOVERY`. The relay's
  per-stream connections carry only the peer version.
- Exchanged right after the relay access handshake, keeping the relay silent towards unauthenticated callers:
  - Connectors: `RelayServerRegistration.runRegistrationLoop`, `DefaultRemoteDiscoveryRepository.runDiscoveryLoop`.
  - Acceptor: `handleServerRegistration` and `handleDiscovery` in `appRelay`'s `DefaultNetworkRelayRepository`.
- Trade-off: a future bump changing the access handshake itself shows up as a wrong passphrase. Acceptable while every component is
  updated together.

## Reporting

### Monitor, peer mismatch

- Connection:
  - `WebSocketConnectionHandler.runSession` maps close code `4000` to `ProtocolVersionMismatchException`.
  - `attemptSafeConnection` treats it as terminal like `PairingRevokedException`: no retry.
  - `DefaultNetworkClientRepository` disconnects with new `ConnectionState.ErrorReason` values `CameraOutdated` / `MonitorOutdated`.
  - `CameraSelectionScreen` shows a string for each.
- Pairing:
  - The `ClosedReceiveChannelException` branch of `runPairing` maps close code `4000` to new `ClientPairingFailureCause` values
    `CAMERA_OUTDATED` / `MONITOR_OUTDATED`.
  - Each gets a pairing error string.

### Camera and monitor, relay mismatch

- New `enum class RelayVersionMismatch { RELAY_OUTDATED, APP_OUTDATED }` in `network:model`.
- Exposed as `relayVersionMismatchFlow: Flow<RelayVersionMismatch?>`:
  - Monitor: on `RemoteDiscoveryRepository`, consumed by `CameraSelectionScreen`.
  - Camera: from `RelayServerRegistration` through `NetworkServerRepository`, consumed by `ServerHomeScreen`.
- UI:
  - The relay status icon stays as it is: a mismatch simply leaves the relay not registered.
  - When the flow turns non-null, the screen shows a snackbar through `LocalSnackbarController`, with one string per
    `RelayVersionMismatch` value.
- Kept apart from `isRegistered`, so `ServerState` and its frame codec are untouched.
- On mismatch the registration/discovery loop stops retrying. It restarts through the existing paths: relay configuration change,
  `setEnabled`, `start()`. Any restart clears the mismatch.

### Log only

- Camera on a peer mismatch, relay on a relay mismatch: `Logger.w` with both versions, then the `4000` close.

## Testing

- `network:internal` `commonTest` (new test module): equal versions proceed; different versions close with `4000` and the
  acceptor's version; malformed frame closes with `PROTOCOL_ERROR`; the close maps to `ProtocolVersionMismatchException` with the
  remote version.
- `network:security` `commonTest`: a `4000` close maps to the right `RelayVersionMismatch`.
- `network:client` `commonTest`: a mismatch maps to `CAMERA_OUTDATED` / `MONITOR_OUTDATED` and `CameraOutdated` / `MonitorOutdated`
  according to the direction.

## Docs

- `AGENTS.md`: mention the two constants in the `network:internal` / `network:security` descriptions and when to bump them; add
  `network:internal` to the test list and extend the `network:security`/`network:client` test descriptions.
