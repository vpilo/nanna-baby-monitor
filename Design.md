# Single-address devices and cached local discovery

Status: merge draft. The shared requirements below are consolidated from
`Design-1.md` and `desiGn-1.md`. Decisions D1–D7 remain open; their alternatives
must not be read as simultaneous implementation requirements. The source specs
are retained until the conflicts are resolved.

## Purpose and success criteria

Expose one selected address per device and let the monitor discover paired local
cameras through saved address hints without waiting for mDNS. Address collections
belong inside discovery, not inside public device or socket-handler objects.

- With saved reachable hints, a running paired camera appears locally after cache
  hydration and within one parallel probe batch, using the existing 2-second
  per-address timeout. Do not add a discovery debounce or wait for other peers.
- `WebSocketConnectionHandler` and `DefaultClientPairingRepository` each connect
  to exactly one supplied address.
- Control, audio, and video use the same active session route.
- A discovery update never moves an established session between local addresses
  or between relay and LAN.
- Existing pairing cryptography, server endpoints, relay access/rendezvous
  protocols, and device transport string formats remain compatible.

An address hint is not proof of reachability or identity. TCP success proves only
that a listener accepts connections. TLS pinning and application authentication
remain authoritative. Unknown new addresses cannot be recovered from a stale
cache while mDNS stays silent.

## Terminology

| Term | Meaning |
| --- | --- |
| Candidate | A local host advertised by discovery or retained as a hint. |
| Selected address | The single address discovery currently offers for a device. |
| Session route | The concrete local address or relay host captured for an active session. |
| Authenticated working address | A local address on which the paired peer completed authentication. |
| Probe round | Concurrent TCP checks of eligible candidates for one peer. |
| Connection attempt | A user/startup/recovery operation whose termination and fallback rules depend on D1. |

## Shared device model

Add `DeviceAddress` in `model`:

```kotlin
@Serializable
@JvmInline
value class DeviceAddress(val value: String)
```

Represent a nonblank host without scheme, explicit port, or endpoint path. The
representation accepts relay hostnames and IP literals, including scoped IPv6.
Construction, equality, hashing, and serialization perform no DNS lookup.
Local discovery derives values from `InetAddress.hostAddress`; network code
performs socket conversion. Keep `java.net` out of the model.

Replace `Device.addresses: Set<InetAddress>` with `address: DeviceAddress?`.
`LocalServer` and `Client` default to `null`; `RemoteServer` and `Relay` require a
non-null address holding the relay host. A typed `relayHost` getter may alias that
same value without duplicating storage.

Preserve concrete-class, ID, and name equality and include the selected address in
equality/hash codes so flow deduplication sees address changes. Preserve name/ID
ordering. Keep `toString()` concise and omit the address; log route targets
explicitly where they are needed. Remove address-set constructors,
`toAddressSet()`, and `EMPTY_ADDRESS`.

Own-device identities, paired-but-unavailable peers, and local previews remain
addressless. `PairedDevice.asServer()` and `.asClient()` do not convert saved hints
directly into connectable devices. Selection changes create new immutable device
snapshots rather than mutating a device held by a session.

Update relay construction, registration comparisons, previews, and tests to use
`DeviceAddress`. `DeviceTransport` still encodes `id#relayHost#name`, now wrapping
the decoded host. `RelayConfiguration.host` stays a settings string.

## Shared discovery architecture

Use one shared coordinator in `network:internal` (working name:
`LocalDiscoveryCoordinator`). Each platform `DefaultLocalDiscoveryRepository`
owns its coordinator and its lifecycle. The coordinator owns candidate records,
selected addresses, all visible-map writes, probe jobs, and stale-result checks.
Avoid a second independent owner of unreachable device snapshots.

Android and Desktop listeners parse validated ID/name/role/address candidates,
exclude the local device, and submit resolution/loss events to the coordinator.
Keep existing schema validation, Android API checks and legacy host fallback,
NSD/JmDNS setup, multicast locking, and advertisement behavior.

Probe only camera/server records against `Constants.SERVICE_PORT`; valid monitor
announcements remain metadata. Paired peers are potential cameras until a live
role announcement identifies them as monitors.

Introduce a fakeable `LocalAddressProbe.acceptsConnections(DeviceAddress)` and a
production TCP implementation using the existing 2-second timeout. Probe different
peers independently and each peer's candidates concurrently. Winner selection is
D3. Do not serialize peer batches.

Cancel replaced probe work. Tag results with discovery generation and candidate
revision so stop, refresh, newer resolutions, and address rejection cannot be
undone by late callbacks. Socket cancellation must actively close blocking
connections; use cleanup on every path.

Start cache hydration and immediate probing alongside client/relay discovery;
camera registration only advertises its identity. Refresh begins a new generation.
Stop cancels jobs, waiters, and sockets and clears transient output without erasing
persisted hints. Publish successful probes immediately without output debounce.

Keep the existing 10-second periodic interval and three consecutive failed-round
threshold for previously visible cameras. A success resets the count. The
watchdog may remain as a scheduler or become a loop inside the coordinator, but
must not retain separate competing state. D4 settles initial publication and mDNS
loss; D5 settles how long unavailable paired hints are retried.

## Persistence requirements and open choices

Persist hints only for currently paired IDs. Skip unchanged writes. Prune stale
records and prevent late work from recreating state for an unpaired peer. Ordinary
TCP timeouts do not invalidate pairing credentials. D2 settles storage and D6
settles which hints are retained and what qualifies as working.

If hints use a separate cache DataStore, keep credentials and the main pairing
JSON untouched. Use a distinct qualified DataStore at
`getCacheDir()/known_addresses.preferences_pb`; missing/read-IO-failed cache data
means no hints. Parse and clean invalid entries. A multi-candidate representation
requires structured per-peer cache values rather than the original one-string
entry.

If hints use pairing JSON, add defaulted candidate/working-address fields for
backward compatibility. Protect every read/modify/write mutation—including pair,
unpair, and rename—with the same repository mutex, re-read under that lock, and
preserve credentials and concurrent peer changes. Write only for real hint changes.

## Single-route transport and pairing

`WebSocketConnectionHandler` takes one route, requires a non-null local address,
and passes the raw host value to Ktor. Retain the relay access path. Remove
recursive host iteration, unused retry plumbing, and the per-host attempt delay
if it has no remaining consumers. Preserve cancellation as cancellation.

Verify Ktor CIO host handling for IPv6 literals and scoped link-local addresses;
do not assume raw socket success guarantees URL/TLS transport support. Document
any unsupported forms and ensure they cannot strand route selection.

`DefaultClientPairingRepository.pairWith()` rejects remote/null-address servers
and tries one host. Preserve PIN confirmation, QR fingerprint checking, protocol
failures, and cancellation. Local-only fallback outside that repository depends
on D1. Semantic failures must not be retried as generic transport failures.

Keep UI local-over-relay display preference by ID. Active session selection is a
separate snapshot: discovery never overwrites it. If D1 adds orchestration, its
owner resolves routes by ID, publishes the session only after authentication,
and lets media handlers consume that same route.

## Conflict register

| ID | `Design-1.md` | `desiGn-1.md` | Proposed synthesis to discuss |
| --- | --- | --- | --- |
| D1: scope | Leave client connection/reconnection behavior unchanged. | Add local alternatives, relay fallback, fresh bounded recovery, startup targeting, and post-authentication success. | Include bounded route orchestration; keep transport handlers single-route. |
| D2: storage | Separate disposable cache; pairing storage unchanged. | Persist hints in sensitive pairing JSON with migration and mutation locking. | Separate cache, with richer hints if D6 chooses them. |
| D3: winner | Wait/rank IPv4 before global/ULA IPv6 before link-local. | First responding address, regardless of family. | Publish first success; keep selection stable while it remains reachable. |
| D4: visibility/loss | Publish best unprobed mDNS address if all probes fail; remove immediately on mDNS loss. | New devices require probe success; loss triggers probing and does not erase responsive devices. | Probe-confirmed publication, loss prompts validation, existing grace period. |
| D5: retry retention | Stop retrying unavailable peers after about five minutes. | Retry paired peers throughout discovery; expire unpaired peers after 30 failed rounds. | Full-session retry for paired peers; bounded retention for unpaired peers. |
| D6: retained hints | One last TCP-responsive address, saved by discovery probes. | Latest advertised set plus last authenticated working address; retain timed-out candidates. | Latest advertised set plus authenticated working address, no unbounded history. |
| D7: certificate mismatch | No changes to current connection-level policy. | Delete rejected candidate, preserve pairing, and continue route fallback. | Decide after D1; distinguish candidate failure from explicit pairing revocation. |

D1 also determines whether to add the discovery selection/feedback APIs,
`ClientConnectionCoordinator`, fake control-connection handles, authentication
callbacks, coordinated media certificate recovery, and pairing/UI fallback. Those
are conditional rather than silently included requirements.

## Verification requirements

Use fake probes and coroutine virtual time for concurrent winner selection,
per-peer independence, stop/refresh races, candidate replacement, and watchdog
behavior. Verify caching, unchanged-write suppression, unpair pruning, and any
chosen storage migration/concurrency contract. Add `network:internal:desktopTest`
and document its coverage in `AGENTS.md` when implementing.

Keep model/transport and pairing regression coverage. If D1 chooses orchestration,
test local alternatives before relay, authentication-before-success, finite
attempts, no revival after exhaustion, fresh recovery candidates, cancellation,
and simultaneous control/media failures with fake connection handles. Preserve
real TLS/handshake regression tests.

Implementation verification will run the affected and existing desktop suites and
the full Android/Desktop builds, with relay compilation because its constructors
change:

```sh
./gradlew :network:internal:desktopTest :network:model:desktopTest :network:client:desktopTest :network:security:desktopTest
./gradlew :errorreport:data:desktopTest :build-logic:test
./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug :appRelay:compileKotlinDesktop
```

Manual acceptance scenarios include monitor restart before mDNS responds, camera
starting later, address changes, IPv4/IPv6/scoped IPv6, network refresh, and all
Android/Desktop combinations. If fallback is included, also verify relay fallback,
keeping a working relay route after LAN discovery, and returning to idle after
exhaustion.

The final implementation sequence and component/API details follow after the
conflict decisions are settled. This task produces the design, not implementation.
