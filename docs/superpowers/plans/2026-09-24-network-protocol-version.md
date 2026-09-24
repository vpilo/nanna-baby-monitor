# Network Protocol Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Version every camera↔monitor connection and every relay registration connection, and tell the user which side to update
when versions differ.

**Architecture:** The connector sends its version as the first text frame; the acceptor continues silently on a match, otherwise
closes with custom close code `4000` carrying its own version. The peer version lives in `network:internal` and is checked in the
session and pairing handshakes; the relay version lives in `network:security` and is checked right after the relay access handshake
on the two registration endpoints only.

**Tech Stack:** Kotlin Multiplatform, Ktor 3.6 WebSockets, Compose Multiplatform resources, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-24-network-protocol-version-design.md`

## Global Constraints

- `PEER_PROTOCOL_VERSION = 1` in `network:internal`; `RELAY_PROTOCOL_VERSION = 1` in `network:security`. Strict equality.
- Mismatch close code: `4000`; close message: the acceptor's version in decimal.
- No backward compatibility with builds lacking the version.
- Peer mismatch: reported on the monitor only; the camera logs. Relay mismatch: existing relay status icon (not registered) plus a
  snackbar on camera and monitor; the relay logs.
- A mismatch is terminal: no retries until a restart path (relay configuration change, `setEnabled`, `start()`, new connect).
- Strings exist in `values`, `values-it`, `values-nl` of `appCommon/src/commonMain/composeResources`. Compose Resources use a bare
  `'`, never `\'`.
- Logging: `Logger.x(TAG) { ... }`; TAG in a private companion object at the bottom of classes, a top-level `private const val TAG`
  in files of top-level functions.
- Extensions go in a `ktx` subpackage, file named `<Receiver>Ktx.kt`.
- Run `./gradlew` with the sandbox disabled (the Kotlin daemon writes to `/tmp`).
- The pre-commit hook builds the staged tree alone: stage every file of a task before committing, never partial files.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Review Focus

1. The camera's relay registration sets `isRegistered = true` before the relay has read the version; after a mismatch the icon must
   settle on "not registered" and the loop must stop, not spin every `RECONNECTION_TIMEOUT`. Covered by the loop exit in Task 5 and
   the manual check in Task 6.
2. A monitor that auto-reconnects to the last server at startup must stop after a version mismatch instead of retrying forever.
   Covered by the auto-reconnection stop list in Task 4.
3. A non-text or non-numeric first frame must be a `PROTOCOL_ERROR`, never a version mismatch. Covered by Task 1 tests.
4. A `4000` close whose message isn't a number must not be reported as a mismatch. Covered by Task 1 and Task 2 tests.
5. On `/pair`, a closed pairing window must not hide a mismatch: the version is read before the window check. Covered in Task 3.

---

### Task 1: Peer protocol version helpers (`network:internal`)

**Files:**
- Create: `network/internal/src/commonMain/kotlin/org/vpilo/babymonitor/network/internal/protocol/ProtocolVersion.kt`
- Test: `network/internal/src/commonTest/kotlin/org/vpilo/babymonitor/network/internal/protocol/ProtocolVersionTest.kt`

**Interfaces:**
- Produces:
  - `const val PEER_PROTOCOL_VERSION: Int = 1`
  - `const val PROTOCOL_VERSION_MISMATCH_CLOSE_CODE: Short = 4000`
  - `suspend fun WebSocketSession.sendProtocolVersion(version: Int)`
  - `suspend fun WebSocketSession.acceptProtocolVersion(version: Int): Boolean`
  - `class ProtocolVersionMismatchException(val localVersion: Int, val remoteVersion: Int) : Exception` with
    `val isRemoteOutdated: Boolean`
  - `fun CloseReason.asProtocolVersionMismatchOrNull(localVersion: Int): ProtocolVersionMismatchException?`

- [ ] **Step 1: Write the failing tests**

```kotlin
package org.vpilo.babymonitor.network.internal.protocol

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolVersionTest {
    @Test
    fun sentVersionIsASingleDecimalTextFrame() =
        runTest {
            val session = FakeWebSocketSession()

            session.sendProtocolVersion(7)

            assertEquals("7", (session.sent.receive() as Frame.Text).readText())
        }

    @Test
    fun matchingVersionIsAcceptedSilently() =
        runTest {
            val session = FakeWebSocketSession()
            session.received.send(Frame.Text("1"))

            assertTrue(session.acceptProtocolVersion(1))
            assertTrue(session.sent.tryReceive().isFailure)
        }

    @Test
    fun differentVersionClosesWithTheAcceptorsVersion() =
        runTest {
            val session = FakeWebSocketSession()
            session.received.send(Frame.Text("2"))

            assertFalse(session.acceptProtocolVersion(1))
            assertEquals(CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, "1"), (session.sent.receive() as Frame.Close).readReason())
        }

    @Test
    fun nonNumericVersionIsAProtocolError() =
        runTest {
            val session = FakeWebSocketSession()
            session.received.send(Frame.Text("one"))

            assertFalse(session.acceptProtocolVersion(1))
            assertEquals(CloseReason.Codes.PROTOCOL_ERROR.code, (session.sent.receive() as Frame.Close).readReason()?.code)
        }

    @Test
    fun binaryVersionIsAProtocolError() =
        runTest {
            val session = FakeWebSocketSession()
            session.received.send(Frame.Binary(true, byteArrayOf(1)))

            assertFalse(session.acceptProtocolVersion(1))
            assertEquals(CloseReason.Codes.PROTOCOL_ERROR.code, (session.sent.receive() as Frame.Close).readReason()?.code)
        }

    @Test
    fun mismatchCloseMapsToTheRemoteVersion() {
        val mismatch = CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, "3").asProtocolVersionMismatchOrNull(localVersion = 2)

        assertEquals(3, mismatch?.remoteVersion)
        assertEquals(2, mismatch?.localVersion)
        assertFalse(mismatch!!.isRemoteOutdated)
    }

    @Test
    fun olderRemoteIsOutdated() {
        val mismatch = CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, "1").asProtocolVersionMismatchOrNull(localVersion = 2)

        assertTrue(mismatch!!.isRemoteOutdated)
    }

    @Test
    fun otherClosesAreNotMismatches() {
        assertNull(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "1").asProtocolVersionMismatchOrNull(localVersion = 1))
        assertNull(CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, "garbage").asProtocolVersionMismatchOrNull(localVersion = 1))
    }
}

private class FakeWebSocketSession : WebSocketSession {
    val received = Channel<Frame>(Channel.UNLIMITED)
    val sent = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val incoming = received
    override val outgoing = sent
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override suspend fun flush() = Unit

    @Deprecated("Use cancel() instead.", level = DeprecationLevel.ERROR)
    override fun terminate() = Unit
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :network:internal:desktopTest --tests '*ProtocolVersionTest*'`
Expected: compilation FAILS with unresolved references `sendProtocolVersion`, `acceptProtocolVersion`, ...

- [ ] **Step 3: Implement**

```kotlin
package org.vpilo.babymonitor.network.internal.protocol

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import org.vpilo.babymonitor.common.Logger

/** Version of the camera↔monitor protocol. Bump on any wire-incompatible change to the peer endpoints. */
const val PEER_PROTOCOL_VERSION: Int = 1

/** Application-defined close code (4000-4999 range) sent by an acceptor whose version differs; the message is its version. */
const val PROTOCOL_VERSION_MISMATCH_CLOSE_CODE: Short = 4000

class ProtocolVersionMismatchException(
    val localVersion: Int,
    val remoteVersion: Int,
) : Exception("Protocol version mismatch: local $localVersion, remote $remoteVersion") {
    val isRemoteOutdated: Boolean
        get() = remoteVersion < localVersion
}

/** Sent by the connector as the very first frame of a connection. */
suspend fun WebSocketSession.sendProtocolVersion(version: Int) = send(Frame.Text(version.toString()))

/**
 * Read by the acceptor as the very first frame. Returns `true` when the versions match; otherwise closes the session and returns
 * `false`. The close carries this side's version so the connector can tell which side is outdated.
 */
suspend fun WebSocketSession.acceptProtocolVersion(version: Int): Boolean {
    val remoteVersion = (incoming.receive() as? Frame.Text)?.readText()?.toIntOrNull()
    if (remoteVersion == null) {
        Logger.w(TAG) { "Malformed protocol version" }
        close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed protocol version"))
        return false
    }
    if (remoteVersion != version) {
        Logger.w(TAG) { "Protocol version mismatch: local $version, remote $remoteVersion" }
        close(CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, version.toString()))
        return false
    }
    return true
}

fun CloseReason.asProtocolVersionMismatchOrNull(localVersion: Int): ProtocolVersionMismatchException? {
    if (code != PROTOCOL_VERSION_MISMATCH_CLOSE_CODE) return null
    val remoteVersion = message.toIntOrNull() ?: return null
    return ProtocolVersionMismatchException(localVersion, remoteVersion)
}

private const val TAG = "ProtocolVersion"
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :network:internal:desktopTest --tests '*ProtocolVersionTest*'`
Expected: 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add network/internal/src/commonMain/kotlin/org/vpilo/babymonitor/network/internal/protocol/ProtocolVersion.kt \
        network/internal/src/commonTest/kotlin/org/vpilo/babymonitor/network/internal/protocol/ProtocolVersionTest.kt
git commit -m "Add the peer protocol version exchange"
```

---

### Task 2: Relay protocol version helpers (`network:security`, `network:model`)

**Files:**
- Create: `network/model/src/commonMain/kotlin/org/vpilo/babymonitor/network/model/RelayVersionMismatch.kt`
- Create: `network/security/src/commonMain/kotlin/org/vpilo/babymonitor/network/security/protocol/RelayVersionProtocol.kt`
- Test: `network/security/src/commonTest/kotlin/org/vpilo/babymonitor/network/security/protocol/RelayVersionProtocolTest.kt`

**Interfaces:**
- Consumes (Task 1): `sendProtocolVersion`, `acceptProtocolVersion`, `asProtocolVersionMismatchOrNull`,
  `PROTOCOL_VERSION_MISMATCH_CLOSE_CODE`.
- Produces:
  - `enum class RelayVersionMismatch { RELAY_OUTDATED, APP_OUTDATED }` (package `org.vpilo.babymonitor.network.model`)
  - `internal const val RELAY_PROTOCOL_VERSION: Int = 1`
  - `suspend fun WebSocketSession.sendRelayProtocolVersion()`
  - `suspend fun WebSocketSession.acceptRelayProtocolVersion(): Boolean`
  - `fun CloseReason.asRelayVersionMismatchOrNull(): RelayVersionMismatch?`

`appRelay` depends on `network:security` but not on `network:internal`, hence these wrappers.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.vpilo.babymonitor.network.security.protocol

import io.ktor.websocket.CloseReason
import org.vpilo.babymonitor.network.internal.protocol.PROTOCOL_VERSION_MISMATCH_CLOSE_CODE
import org.vpilo.babymonitor.network.model.RelayVersionMismatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelayVersionProtocolTest {
    @Test
    fun newerRelayMeansTheAppIsOutdated() {
        assertEquals(
            RelayVersionMismatch.APP_OUTDATED,
            CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, (RELAY_PROTOCOL_VERSION + 1).toString()).asRelayVersionMismatchOrNull(),
        )
    }

    @Test
    fun olderRelayIsOutdated() {
        assertEquals(
            RelayVersionMismatch.RELAY_OUTDATED,
            CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, (RELAY_PROTOCOL_VERSION - 1).toString()).asRelayVersionMismatchOrNull(),
        )
    }

    @Test
    fun otherClosesAreNotMismatches() {
        assertNull(CloseReason(CloseReason.Codes.NORMAL, "").asRelayVersionMismatchOrNull())
        assertNull(CloseReason(PROTOCOL_VERSION_MISMATCH_CLOSE_CODE, "").asRelayVersionMismatchOrNull())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :network:security:desktopTest --tests '*RelayVersionProtocolTest*'`
Expected: compilation FAILS with unresolved references.

- [ ] **Step 3: Implement**

`RelayVersionMismatch.kt`:

```kotlin
package org.vpilo.babymonitor.network.model

enum class RelayVersionMismatch {
    RELAY_OUTDATED,
    APP_OUTDATED,
}
```

`RelayVersionProtocol.kt`:

```kotlin
package org.vpilo.babymonitor.network.security.protocol

import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import org.vpilo.babymonitor.network.internal.protocol.acceptProtocolVersion
import org.vpilo.babymonitor.network.internal.protocol.asProtocolVersionMismatchOrNull
import org.vpilo.babymonitor.network.internal.protocol.sendProtocolVersion
import org.vpilo.babymonitor.network.model.RelayVersionMismatch

/**
 * Version of the relay registration protocol (`SERVER_REGISTRATION`, `CLIENT_DISCOVERY`). Bump on any wire-incompatible change
 * between the apps and the relay. Relayed peer streams carry the peer version instead.
 */
internal const val RELAY_PROTOCOL_VERSION: Int = 1

suspend fun WebSocketSession.sendRelayProtocolVersion() = sendProtocolVersion(RELAY_PROTOCOL_VERSION)

suspend fun WebSocketSession.acceptRelayProtocolVersion(): Boolean = acceptProtocolVersion(RELAY_PROTOCOL_VERSION)

fun CloseReason.asRelayVersionMismatchOrNull(): RelayVersionMismatch? =
    asProtocolVersionMismatchOrNull(RELAY_PROTOCOL_VERSION)?.let {
        if (it.isRemoteOutdated) RelayVersionMismatch.RELAY_OUTDATED else RelayVersionMismatch.APP_OUTDATED
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :network:security:desktopTest --tests '*RelayVersionProtocolTest*'`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add network/model/src/commonMain/kotlin/org/vpilo/babymonitor/network/model/RelayVersionMismatch.kt \
        network/security/src/commonMain/kotlin/org/vpilo/babymonitor/network/security/protocol/RelayVersionProtocol.kt \
        network/security/src/commonTest/kotlin/org/vpilo/babymonitor/network/security/protocol/RelayVersionProtocolTest.kt
git commit -m "Add the relay protocol version exchange"
```

---

### Task 3: Camera accepts the peer version (`network:server`)

**Files:**
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/session/ServerSessionHandshake.kt`
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/pairing/PairingCoordinator.kt:77-86`

**Interfaces:**
- Consumes (Task 1): `acceptProtocolVersion`, `PEER_PROTOCOL_VERSION`.

No unit test: both functions need a paired storage / pairing window harness that doesn't exist; covered by the Task 6 manual check.

- [ ] **Step 1: Session handshake.** In `serverSessionHandshake`, before `receiveSessionHandshakeRequestOrNull()`:

```kotlin
    if (!acceptProtocolVersion(PEER_PROTOCOL_VERSION)) return null
```

Add imports `org.vpilo.babymonitor.network.internal.protocol.PEER_PROTOCOL_VERSION` and
`org.vpilo.babymonitor.network.internal.protocol.acceptProtocolVersion`. Update the KDoc: the handshake first checks the peer protocol
version.

- [ ] **Step 2: Pairing.** In `PairingCoordinator.handlePairingSession`, as the first statement, before `val window = activeWindow`:

```kotlin
        // Before the window check, so a closed window doesn't hide an incompatible monitor.
        if (!session.acceptProtocolVersion(PEER_PROTOCOL_VERSION)) return
```

Add the same two imports.

- [ ] **Step 3: Compile**

Run: `./gradlew :network:server:compileKotlinDesktop :network:server:compileAndroidMain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/session/ServerSessionHandshake.kt \
        network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/pairing/PairingCoordinator.kt
git commit -m "Check the peer protocol version on the camera"
```

---

### Task 4: Monitor sends the peer version and reports mismatches (`network:client`, `model`, `appCommon`)

**Files:**
- Create: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ktx/ProtocolVersionMismatchExceptionKtx.kt`
- Test: `network/client/src/commonTest/kotlin/org/vpilo/babymonitor/network/client/ktx/ProtocolVersionMismatchExceptionKtxTest.kt`
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ConnectionState.kt` (enum `ErrorReason`)
- Modify: `network/model/src/commonMain/kotlin/org/vpilo/babymonitor/network/model/pairing/ClientPairingFailureCause.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/session/ClientSessionHandshake.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/WebSocketConnectionHandler.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/pairing/DefaultClientPairingRepository.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreen.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreenViewModel.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/pairing/ClientPairingScreen.kt`
- Modify: `appCommon/src/commonMain/composeResources/values/strings.xml`, `values-it/strings.xml`, `values-nl/strings.xml`

**Interfaces:**
- Consumes (Task 1): `sendProtocolVersion`, `PEER_PROTOCOL_VERSION`, `ProtocolVersionMismatchException`,
  `asProtocolVersionMismatchOrNull`.
- Produces:
  - `ConnectionState.ErrorReason.CameraOutdated`, `ConnectionState.ErrorReason.MonitorOutdated`
  - `ClientPairingFailureCause.CAMERA_OUTDATED`, `ClientPairingFailureCause.MONITOR_OUTDATED`
  - `internal fun ProtocolVersionMismatchException.asConnectionErrorReason(): ConnectionState.ErrorReason`
  - `internal fun ProtocolVersionMismatchException.asPairingFailureCause(): ClientPairingFailureCause`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.vpilo.babymonitor.network.client.ktx

import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.network.internal.protocol.ProtocolVersionMismatchException
import org.vpilo.babymonitor.network.model.pairing.ClientPairingFailureCause
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolVersionMismatchExceptionKtxTest {
    private val cameraOlder = ProtocolVersionMismatchException(localVersion = 2, remoteVersion = 1)
    private val cameraNewer = ProtocolVersionMismatchException(localVersion = 1, remoteVersion = 2)

    @Test
    fun olderCameraMustBeUpdated() {
        assertEquals(ConnectionState.ErrorReason.CameraOutdated, cameraOlder.asConnectionErrorReason())
        assertEquals(ClientPairingFailureCause.CAMERA_OUTDATED, cameraOlder.asPairingFailureCause())
    }

    @Test
    fun newerCameraMeansThisMonitorMustBeUpdated() {
        assertEquals(ConnectionState.ErrorReason.MonitorOutdated, cameraNewer.asConnectionErrorReason())
        assertEquals(ClientPairingFailureCause.MONITOR_OUTDATED, cameraNewer.asPairingFailureCause())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :network:client:desktopTest --tests '*ProtocolVersionMismatchExceptionKtxTest*'`
Expected: compilation FAILS with unresolved references.

- [ ] **Step 3: Domain values.** Append `CameraOutdated` and `MonitorOutdated` to `ConnectionState.ErrorReason`, and
  `CAMERA_OUTDATED` and `MONITOR_OUTDATED` to `ClientPairingFailureCause`.

- [ ] **Step 4: Mapping extensions** (`ProtocolVersionMismatchExceptionKtx.kt`):

```kotlin
package org.vpilo.babymonitor.network.client.ktx

import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.network.internal.protocol.ProtocolVersionMismatchException
import org.vpilo.babymonitor.network.model.pairing.ClientPairingFailureCause

internal fun ProtocolVersionMismatchException.asConnectionErrorReason(): ConnectionState.ErrorReason =
    if (isRemoteOutdated) ConnectionState.ErrorReason.CameraOutdated else ConnectionState.ErrorReason.MonitorOutdated

internal fun ProtocolVersionMismatchException.asPairingFailureCause(): ClientPairingFailureCause =
    if (isRemoteOutdated) ClientPairingFailureCause.CAMERA_OUTDATED else ClientPairingFailureCause.MONITOR_OUTDATED
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :network:client:desktopTest --tests '*ProtocolVersionMismatchExceptionKtxTest*'`
Expected: 2 tests PASS.

- [ ] **Step 6: Session handshake.** In `clientSessionHandshake`, after the `pairedDevice == null` check and before
  `val sharedSecret = ...`:

```kotlin
    sendProtocolVersion(PEER_PROTOCOL_VERSION)
```

Imports: `org.vpilo.babymonitor.network.internal.protocol.PEER_PROTOCOL_VERSION`,
`org.vpilo.babymonitor.network.internal.protocol.sendProtocolVersion`.

- [ ] **Step 7: Connection handler.** In `WebSocketConnectionHandler.runSession`, map the mismatch close before the `knownReason`
  switch:

```kotlin
            val reason = closeReason.await() ?: throw ex
            reason.asProtocolVersionMismatchOrNull(PEER_PROTOCOL_VERSION)?.let { throw it }
            throw when (reason.knownReason) {
```

Update its KDoc: a version mismatch close becomes a `ProtocolVersionMismatchException`. In `attemptSafeConnection`, add a terminal
branch after `is PairingRevokedException`:

```kotlin
                is ProtocolVersionMismatchException -> {
                    Logger.w(TAG) { "Incompatible server $target for $endpointPath: ${lastException.prettify()}" }
                    connectionJob = null
                    onDisconnected(lastException)
                }
```

Imports: `PEER_PROTOCOL_VERSION`, `ProtocolVersionMismatchException`, `asProtocolVersionMismatchOrNull` from
`org.vpilo.babymonitor.network.internal.protocol`.

- [ ] **Step 8: Client repository.** In `DefaultNetworkClientRepository.onControlConnectionClosed`, add to the `when (exception)`
  before `is ProtocolException`:

```kotlin
            is ProtocolVersionMismatchException -> {
                Logger.w(TAG) { "Incompatible protocol with $server: ${exception.prettify()}" }
                disconnect(exception.asConnectionErrorReason())
                return
            }
```

Imports: `org.vpilo.babymonitor.network.internal.protocol.ProtocolVersionMismatchException`,
`org.vpilo.babymonitor.network.client.ktx.asConnectionErrorReason`. In the `is ProtocolException` branch, change the log to
`"Server reported a protocol issue"` (the version case is now explicit).

- [ ] **Step 9: Pairing.** In `DefaultClientPairingRepository.runPairing`, send the version before the hello:

```kotlin
            val clientKeyPair = EcdhKeyPair.create()
            sendProtocolVersion(PEER_PROTOCOL_VERSION)
            sendPairingHello(...)
```

and in the `ClosedReceiveChannelException` catch, before building the `when (reason.knownReason)` failure:

```kotlin
            val reason = closeReason.await() ?: throw ex
            reason.asProtocolVersionMismatchOrNull(PEER_PROTOCOL_VERSION)?.let {
                Logger.w(TAG) { "Cannot pair with $server: ${it.message}" }
                return ClientPairingState.Failure(it.asPairingFailureCause())
            }
```

Imports: `PEER_PROTOCOL_VERSION`, `sendProtocolVersion`, `asProtocolVersionMismatchOrNull` from
`org.vpilo.babymonitor.network.internal.protocol`; `org.vpilo.babymonitor.network.client.ktx.asPairingFailureCause`.

- [ ] **Step 10: Strings.** Add after `client_connection_chooser_certificate_mismatch` and after `client_pairing_qr_wrong_device`:

`values/strings.xml`:

```xml
    <string name="client_connection_chooser_camera_outdated">The camera app is outdated. Update it to connect.</string>
    <string name="client_connection_chooser_monitor_outdated">This app is older than the camera's. Update it to connect.</string>
```

```xml
    <string name="client_pairing_failed_camera_outdated">The camera app is outdated. Update it to pair.</string>
    <string name="client_pairing_failed_monitor_outdated">This app is older than the camera's. Update it to pair.</string>
```

`values-it/strings.xml`:

```xml
    <string name="client_connection_chooser_camera_outdated">L'app della telecamera non è aggiornata. Aggiornala per connetterti.</string>
    <string name="client_connection_chooser_monitor_outdated">Questa app è meno recente di quella della telecamera. Aggiornala per connetterti.</string>
```

```xml
    <string name="client_pairing_failed_camera_outdated">L'app della telecamera non è aggiornata. Aggiornala per associarla.</string>
    <string name="client_pairing_failed_monitor_outdated">Questa app è meno recente di quella della telecamera. Aggiornala per associarla.</string>
```

`values-nl/strings.xml`:

```xml
    <string name="client_connection_chooser_camera_outdated">De camera-app is verouderd. Werk hem bij om verbinding te maken.</string>
    <string name="client_connection_chooser_monitor_outdated">Deze app is ouder dan die van de camera. Werk hem bij om verbinding te maken.</string>
```

```xml
    <string name="client_pairing_failed_camera_outdated">De camera-app is verouderd. Werk hem bij om te koppelen.</string>
    <string name="client_pairing_failed_monitor_outdated">Deze app is ouder dan die van de camera. Werk hem bij om te koppelen.</string>
```

- [ ] **Step 11: UI mapping.**
  - `CameraSelectionScreen.getConnectionStateMessage`, in the `Disconnected` branch:

```kotlin
                ConnectionState.ErrorReason.CameraOutdated -> {
                    label = Res.string.client_connection_chooser_camera_outdated
                }

                ConnectionState.ErrorReason.MonitorOutdated -> {
                    label = Res.string.client_connection_chooser_monitor_outdated
                }
```

  - `ClientPairingScreen`, in the `Failure` `when`:

```kotlin
                        ClientPairingFailureCause.CAMERA_OUTDATED -> Res.string.client_pairing_failed_camera_outdated
                        ClientPairingFailureCause.MONITOR_OUTDATED -> Res.string.client_pairing_failed_monitor_outdated
```

  - `CameraSelectionScreenViewModel`, auto-reconnection stop list (Review Focus 2):

```kotlin
                    ConnectionState.ErrorReason.ClientQuit,
                    ConnectionState.ErrorReason.PairingRevoked,
                    ConnectionState.ErrorReason.ServerNotFound,
                    ConnectionState.ErrorReason.CameraOutdated,
                    ConnectionState.ErrorReason.MonitorOutdated,
                        -> {
```

  Add the matching `Res.string.*` imports.

- [ ] **Step 12: Build and test**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug :network:client:desktopTest`
Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 13: Commit**

```bash
git add model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ConnectionState.kt \
        network/model/src/commonMain/kotlin/org/vpilo/babymonitor/network/model/pairing/ClientPairingFailureCause.kt \
        network/client/src appCommon/src
git commit -m "Report peer protocol version mismatches on the monitor"
```

---

### Task 5: Relay registration version (`appRelay`, `network:server`, `network:client`, `appCommon`)

**Files:**
- Modify: `appRelay/src/desktopMain/kotlin/org/vpilo/babymonitor/relay/DefaultNetworkRelayRepository.kt` (`handleDiscovery`,
  `handleServerRegistration`)
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/RelayServerRegistration.kt`
- Modify: `network/model/src/commonMain/kotlin/org/vpilo/babymonitor/network/model/repository/NetworkServerRepository.kt`
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/DefaultNetworkServerRepository.kt`
- Modify: `network/model/src/commonMain/kotlin/org/vpilo/babymonitor/network/model/repository/RemoteDiscoveryRepository.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/discovery/DefaultRemoteDiscoveryRepository.kt`
- Create: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/ktx/RelayVersionMismatchKtx.kt`
- Create: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreenEffect.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreenViewModel.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreen.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreenEffect.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreenViewModel.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreen.kt`
- Modify: `appCommon/src/commonMain/composeResources/values/strings.xml`, `values-it/strings.xml`, `values-nl/strings.xml`

**Interfaces:**
- Consumes (Task 2): `RelayVersionMismatch`, `sendRelayProtocolVersion`, `acceptRelayProtocolVersion`,
  `asRelayVersionMismatchOrNull`.
- Produces:
  - `NetworkServerRepository.relayVersionMismatchFlow: Flow<RelayVersionMismatch?>`
  - `RemoteDiscoveryRepository.relayVersionMismatchFlow: Flow<RelayVersionMismatch?>`
  - `ServerHomeScreenEffect.AnnounceRelayVersionMismatch(mismatch)`, `CameraSelectionScreenEffect.AnnounceRelayVersionMismatch(mismatch)`
  - `val RelayVersionMismatch.messageResource: StringResource`

The mapping logic is tested in Task 2; the loop behavior is covered by the Task 6 manual check.

- [ ] **Step 1: Relay acceptor.** In `DefaultNetworkRelayRepository`:
  - `handleDiscovery`: after `setupSession(...) ?: return`, add `if (!acceptRelayProtocolVersion()) return`.
  - `handleServerRegistration`: after `setupSession(...) ?: return`, add `if (!acceptRelayProtocolVersion()) return`.
  - Import `org.vpilo.babymonitor.network.security.protocol.acceptRelayProtocolVersion`. The helper logs the mismatch.

- [ ] **Step 2: Camera registration.** In `RelayServerRegistration`:

```kotlin
    private val _relayVersionMismatch = MutableStateFlow<RelayVersionMismatch?>(null)
    val relayVersionMismatch: Flow<RelayVersionMismatch?> = _relayVersionMismatch.asStateFlow()
```

In `restart()`, after `stop()`: `_relayVersionMismatch.value = null`. In `runRegistrationLoop`, send the version first and check the
close once the session ends:

```kotlin
                ) {
                    sendRelayProtocolVersion()
                    runWebSocketCatching(TAG) {
                        send(Frame.Text(server.asTransportString(relayConfiguration.host)))
                        Logger.i(TAG) { "Registered with relay as $server" }
                        _isRegistered.value = true
                        readRelaySignals()
                    }
                    _relayVersionMismatch.value = closeReason.await()?.asRelayVersionMismatchOrNull()
                }
```

and after `_isRegistered.value = false`, before `delay(...)`:

```kotlin
            _relayVersionMismatch.value?.let {
                Logger.w(TAG) { "Relay protocol version mismatch ($it), not retrying" }
                return
            }
```

Imports: `RelayVersionMismatch`, `sendRelayProtocolVersion`, `asRelayVersionMismatchOrNull`.

- [ ] **Step 3: Server repository.** Add `val relayVersionMismatchFlow: Flow<RelayVersionMismatch?>` to `NetworkServerRepository`,
  and in `DefaultNetworkServerRepository`:

```kotlin
    override val relayVersionMismatchFlow: Flow<RelayVersionMismatch?> = relayRegistration.relayVersionMismatch
```

- [ ] **Step 4: Monitor discovery.** Add `val relayVersionMismatchFlow: Flow<RelayVersionMismatch?>` to `RemoteDiscoveryRepository`.
  In `DefaultRemoteDiscoveryRepository`:

```kotlin
    private val mutableRelayVersionMismatchFlow = MutableStateFlow<RelayVersionMismatch?>(null)
    override val relayVersionMismatchFlow: Flow<RelayVersionMismatch?> = mutableRelayVersionMismatchFlow.asStateFlow()
```

  - In `setRelay`, next to `_discoveredDevicesFlow.value = emptySet()`: `mutableRelayVersionMismatchFlow.value = null`.
  - In `runDiscoveryLoop`, inside the `relayWss` block, send the version before `session = this`, and read the close after
    `runWebSocketCatching`:

```kotlin
                    sendRelayProtocolVersion()
                    session = this
                    mutableIsRegisteredFlow.value = true
                    runWebSocketCatching(TAG) {
                        // unchanged frame loop
                    }
                    mutableRelayVersionMismatchFlow.value = closeReason.await()?.asRelayVersionMismatchOrNull()
```

  - After `_discoveredDevicesFlow.value = emptySet()` at the end of the loop body, before `delay(...)`:

```kotlin
            mutableRelayVersionMismatchFlow.value?.let {
                Logger.w(TAG) { "Relay protocol version mismatch ($it), not retrying" }
                return
            }
```

- [ ] **Step 5: Strings.** Add at the end of each `strings.xml`:

`values`:

```xml
    <string name="relay_version_mismatch_relay_outdated">The relay server is outdated. Update it to use the relay.</string>
    <string name="relay_version_mismatch_app_outdated">This app is older than the relay server. Update it to use the relay.</string>
```

`values-it`:

```xml
    <string name="relay_version_mismatch_relay_outdated">Il server relay non è aggiornato. Aggiornalo per usare il relay.</string>
    <string name="relay_version_mismatch_app_outdated">Questa app è meno recente del server relay. Aggiornala per usare il relay.</string>
```

`values-nl`:

```xml
    <string name="relay_version_mismatch_relay_outdated">De relayserver is verouderd. Werk hem bij om de relay te gebruiken.</string>
    <string name="relay_version_mismatch_app_outdated">Deze app is ouder dan de relayserver. Werk hem bij om de relay te gebruiken.</string>
```

- [ ] **Step 6: Shared message mapping** (`app/ktx/RelayVersionMismatchKtx.kt`):

```kotlin
package org.vpilo.babymonitor.app.ktx

import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.relay_version_mismatch_app_outdated
import babymonitor.appcommon.generated.resources.relay_version_mismatch_relay_outdated
import org.jetbrains.compose.resources.StringResource
import org.vpilo.babymonitor.network.model.RelayVersionMismatch

val RelayVersionMismatch.messageResource: StringResource
    get() =
        when (this) {
            RelayVersionMismatch.RELAY_OUTDATED -> Res.string.relay_version_mismatch_relay_outdated
            RelayVersionMismatch.APP_OUTDATED -> Res.string.relay_version_mismatch_app_outdated
        }
```

- [ ] **Step 7: Camera snackbar.**
  - `ServerHomeScreenEffect.kt`:

```kotlin
package org.vpilo.babymonitor.app.server.home

import org.vpilo.babymonitor.network.model.RelayVersionMismatch

sealed interface ServerHomeScreenEffect {
    data class AnnounceRelayVersionMismatch(
        val mismatch: RelayVersionMismatch,
    ) : ServerHomeScreenEffect
}
```

  - `ServerHomeScreenViewModel`: change the effect type parameter from `Unit` to `ServerHomeScreenEffect`, and in `onSubscribed()`:

```kotlin
        server.relayVersionMismatchFlow.filterNotNull().subscribe { mismatch ->
            ServerHomeScreenEffect.AnnounceRelayVersionMismatch(mismatch).sendEffect()
        }
```

  - `ServerHomeScreen`, after `val state by ...`:

```kotlin
    val snackbarController = LocalSnackbarController.current

    LaunchedEffect(viewModel.effectsFlow) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is ServerHomeScreenEffect.AnnounceRelayVersionMismatch -> {
                    snackbarController.show(message = getString(effect.mismatch.messageResource))
                }
            }
        }
    }
```

  Imports: `androidx.compose.runtime.LaunchedEffect`, `org.jetbrains.compose.resources.getString`,
  `org.vpilo.babymonitor.app.ktx.messageResource`, `org.vpilo.babymonitor.presentation.snackbar.LocalSnackbarController`.

- [ ] **Step 8: Monitor snackbar.**
  - `CameraSelectionScreenEffect`: add

```kotlin
    data class AnnounceRelayVersionMismatch(
        val mismatch: RelayVersionMismatch,
    ) : CameraSelectionScreenEffect
```

  - `CameraSelectionScreenViewModel.onSubscribed()`, next to the `remoteDiscoveryRepository.isRegisteredFlow` subscription:

```kotlin
        remoteDiscoveryRepository.relayVersionMismatchFlow.filterNotNull().subscribe { mismatch ->
            CameraSelectionScreenEffect.AnnounceRelayVersionMismatch(mismatch).sendEffect()
        }
```

  - `CameraSelectionScreen`, in the effects `when`:

```kotlin
                is CameraSelectionScreenEffect.AnnounceRelayVersionMismatch -> {
                    snackbarController.show(message = getString(effect.mismatch.messageResource))
                }
```

  Imports: `kotlinx.coroutines.flow.filterNotNull` (both VMs), `org.vpilo.babymonitor.app.ktx.messageResource`,
  `org.vpilo.babymonitor.network.model.RelayVersionMismatch`.

- [ ] **Step 9: Build**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug :appRelay:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add appRelay/src network/model/src network/server/src network/client/src appCommon/src
git commit -m "Version the relay registration connections"
```

---

### Task 6: Docs and verification

**Files:**
- Modify: `AGENTS.md` (`.claude/CLAUDE.md` is a symlink to it)

- [ ] **Step 1: Update `AGENTS.md`.**
  - `network:internal` bullet: append "the peer protocol version (`PEER_PROTOCOL_VERSION`) and its first-frame exchange".
  - `network:security` bullet: append "the relay registration protocol version (`RELAY_PROTOCOL_VERSION`)".
  - Add under Key Patterns:

```markdown
- **Protocol versions** - `PEER_PROTOCOL_VERSION` (camera↔monitor, `network:internal`) and `RELAY_PROTOCOL_VERSION` (relay
  registration, `network:security`) must match exactly; the connector sends its version as the first frame and a mismatch closes
  with code `4000`. Bump the matching constant on any wire-incompatible change.
```

  - Tests bullet: add `network:internal` (protocol version exchange), extend `network:security` with "relay version mapping" and
    `network:client` with "version mismatch mapping"; add `:network:internal:desktopTest` to the run command.

- [ ] **Step 2: Full test run**

Run: `./gradlew :network:internal:desktopTest :network:security:desktopTest :network:model:desktopTest :network:client:desktopTest :errorreport:data:desktopTest :build-logic:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual check (throwaway, never committed).** Temporarily set `PEER_PROTOCOL_VERSION = 2` and
  `RELAY_PROTOCOL_VERSION = 2`, run `./gradlew :appDesktop:run`, and against an Android build with version 1 and a relay with
  version 1 confirm:
  - Desktop monitor → Android camera: "This app is older than the camera's…" and no reconnection loop (Review Focus 2).
  - Desktop monitor pairing with the Android camera, pairing window closed: the version message, not "not in progress" (Review
    Focus 5).
  - Desktop camera and monitor with the relay: relay icon off, one snackbar "This app is older than the relay server…", and the
    logs show no retries every 3 s (Review Focus 1).
  - Revert both constants; `git diff` shows no change to them.

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md
git commit -m "Document the network protocol versions"
```
