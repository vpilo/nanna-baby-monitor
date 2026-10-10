# Design: single device address and known-address probing

## Goals

- Replace `Device.addresses: Set<InetAddress>` with a single `DeviceAddress`.
- Local discovery probes all advertised addresses and keeps the best reachable one.
- Remember the last working address of each paired server; on discovery start, probe those addresses so active cameras show up as
  local immediately, without waiting for mDNS (which often lags behind the relay).

## Success criteria

- A paired local server running before the monitor starts shows as local within one probe timeout (~2s) of discovery start.
- `WebSocketConnectionHandler` and `DefaultClientPairingRepository` no longer iterate over hosts.
- Control, audio and video connections always use the same address.

## Non-goals

- No reconnection logic changes: a failed connection still drops to camera selection; mDNS or the relay make the server visible again.
- No live switching of an active session between relay and LAN.
- Server side and the relay wire format are unchanged.

## 1. Model

```kotlin
// model/.../DeviceAddress.kt
@JvmInline
value class DeviceAddress(val value: String)

sealed class Device(
    val id: DeviceId,
    val name: String,
    val address: DeviceAddress?,
) : Comparable<Device>

abstract class Server(id, name, address) : Device(...)
class LocalServer(id, name, address: DeviceAddress? = null) : Server(...)
class RemoteServer(id, name, relayHost: DeviceAddress) : Server(id, name, relayHost) {
    val relayHost: DeviceAddress get() = checkNotNull(address)
}
class Client(id, name, address: DeviceAddress? = null) : Device(...)
class Relay(id, name, relayHost: DeviceAddress) : Device(id, name, relayHost) { /* same getter */ }
```

- `address == null`: not known to be directly reachable (own device, paired server from storage, previews).
- `RemoteServer`/`Relay` store the relay host as their address; `relayHost` is a typed getter.
- `equals`/`hashCode` include `id`, `name`, `address`, so address changes propagate through `distinctUntilChanged`.
- `compareTo` stays name, then id. `toString` omits the address.
- Removed: secondary constructors, `toAddressSet()`, `EMPTY_ADDRESS`. `model` no longer imports `java.net`.
- `DeviceAddress.toInetAddress()` lives in a `network:internal` `ktx` (`InetAddress.getByName` on IP literals does no DNS lookup).
  Link-local addresses keep their scope suffix (`fe80::…%wlan0`).
- `DeviceAddress` is used everywhere, including `relayHost` in `DeviceTransport.kt` and relay registration checks (via `.value`). The
  transport string format is unchanged.
- `PairedDevice` is unchanged; `asServer()` returns a `LocalServer` without address.

## 2. Discovery

### Listeners

- `AndroidDiscoveryListener` and `DesktopDiscoveryListener` only parse resolved services into candidates (id, name, type, advertised
  addresses) and hand them to `AddressTracker`. They no longer write the discovered devices map and need no coroutine scope.
- Service lost events are forwarded to `AddressTracker.onLost(id)`.

### `AddressTracker`

Common class in `network:internal`, held by both platform `DefaultLocalDiscoveryRepository` actuals. Replaces `DeviceWatchdog`
(deleted). Depends on the discovered devices `MutableStateFlow<Map<DeviceId, Device>>`, a `CoroutineScope`,
`PairingStorageRepository` and `KnownAddressRepository`. It owns all probing and all map writes.

- **`onResolved(candidate)`**
  - Cancels any in-flight probe for the same ID.
  - Probes all advertised addresses in parallel (`acceptsConnections()`, TCP connect to `Constants.SERVICE_PORT`).
  - Ranks reachable addresses: IPv4 > IPv6 global/ULA > IPv6 link-local; stable among equal ranks.
  - Writes the best reachable address into the map, replacing any existing entry (mDNS always wins).
  - If none is reachable, writes the best-ranked address unprobed; the periodic loop removes it if it never answers.
- **`onLost(id)`**: removes the entry.
- **`start()`**
  - Loads known addresses and paired IDs; calls `KnownAddressRepository.retainOnly(pairedIds)`.
  - Probes the known address of every paired server not yet in the map; hits are inserted as `LocalServer` only if the ID is still
    absent (the prober only fills gaps, never overwrites mDNS).
  - Misses go to the unreachable set, so they keep being probed for the unreachable window.
  - Starts the periodic loop, same behavior as the former watchdog: probe every 10s, remove after 3 consecutive failures, keep
    probing removed devices for ~5 minutes, devices announced again by mDNS leave the unreachable set.
- **Persistence**: every successful probe of a paired server whose address differs from the known one calls
  `KnownAddressRepository.save(id, address)`. Applies to resolves, the startup probe and the periodic loop.
- **`stop()`**: cancels all jobs, clears the unreachable set.

### Repository wiring

- `register(Client/Relay)` calls `addressTracker.start()`; `unregister()` calls `stop()`; `refresh()` does both.
- Known addresses are probed once per discovery start or refresh.
- `Device.isReachable()` is replaced by `DeviceAddress.acceptsConnections()` plus an address ranking helper.

## 3. Connection layer and pairing

### `WebSocketConnectionHandler`

- `connect()`: `RemoteServer` goes through the relay; otherwise `startWebSocket(checkNotNull(device.address))`.
- Removed: `connect(remainingHosts)`, the recursive `doConnect`, `retryJob` (never assigned), and
  `Constants.WEBSOCKET_CONNECTION_ATTEMPT_DELAY` if unused elsewhere.
- An unreachable local host calls `onDisconnected(ConnectException(...))`.
- `startWebSocket(host: DeviceAddress)` passes `host.value` to `wss(host = …)`. To verify: Ktor CIO handling of IPv6 literals and
  scoped link-local addresses (`[fe80::1%25wlan0]`). If link-local can't work, it stays as last-ranked with a documented limitation.
- Logs target `device.address` instead of `host.hashCode()`.

### `DefaultClientPairingRepository.pairWith`

- Guard: `server !is LocalServer || server.address == null` → `SERVER_NOT_ON_NETWORK`.
- Single `pairWithHost(server.address, …)` call instead of iterating; `GENERIC_FAILURE` remains the fallthrough result.

### Unchanged

- `DefaultNetworkClientRepository` (no persistence from the connection layer, no reconnection changes).
- Use cases: `GetVisibleServersFlowUseCase` keeps preferring local over relay by ID.
- Server-side registration and `ServerHomeScreenViewModel`.

## 4. Persistence

- Pairing storage is not touched: it's a single sensitive JSON blob in the main settings DataStore, and every DataStore edit rewrites
  the whole file.
- New `KnownAddressRepository` interface in `model/.../repository/` (visible to both `settings:data` and `network:internal`):
  - `suspend fun load(): Map<DeviceId, DeviceAddress>`
  - `suspend fun save(id: DeviceId, address: DeviceAddress)`
  - `suspend fun retainOnly(ids: Set<DeviceId>)`
- `DefaultKnownAddressRepository` in `settings:data`, backed by a second `DataStore<Preferences>` at
  `getCacheDir()/known_addresses.preferences_pb`, registered in `settingsDataKoinModule` under a named qualifier.
  - Cache dir: addresses are disposable hints; losing them only means waiting for mDNS once.
  - One `stringPreferencesKey(deviceId.toString())` per device holding `address.value`; no JSON.
  - `save()` skips unchanged values, so periodic successful probes cause no I/O.
  - Read `IOException` yields an empty map. Unparsable ID keys are dropped by `retainOnly`.
- Unpairing needs no changes: stale entries are pruned on the next `start()`.

## 5. Testing

- **`network:internal` `desktopTest`** (new source set):
  - Ranking: IPv4 > IPv6 global/ULA > link-local, stable among equal ranks.
  - `AddressTracker` with an injected `probe: suspend (DeviceAddress) -> Boolean`, fake repositories, virtual time (`runTest`):
    - mDNS replaces a prober entry; the prober never overwrites an mDNS entry.
    - A resolve with no reachable address inserts the best-ranked one unprobed.
    - A newer resolve for the same ID cancels the in-flight probe.
    - A known address missing at start returns within the unreachable window.
    - Removal after 3 consecutive failures.
    - Persistence only for paired IDs and only on change.
    - `retainOnly` prunes unpaired IDs.
- **`network:client`**: `DefaultClientPairingRepositoryTest` uses a single `DeviceAddress("127.0.0.1")`.
- **`settings:data`**: no tests (thin DataStore glue).
- **`AGENTS.md`**: add `network:internal` to tested modules and the test command; document `KnownAddressRepository` and
  `AddressTracker` in the module descriptions.
- **Manual**:
  - Two devices on one LAN, camera running; restart the monitor: camera shows as local within ~2s.
  - Change the camera IP: after the drop to camera selection, mDNS brings it back with the new address, and the known address is
    updated.
