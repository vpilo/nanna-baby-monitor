# Client Keeps Audio + Control Connection Alive on Lock — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the client's audio + control WebSockets alive while the Android device screen is locked, by adding a role-aware foreground service for the client and moving control-channel reconnect ownership from the UI into the network handler.

**Architecture:**
- `AndroidService` interface gains `val role: AppRole`. `AndroidServiceRegistry` rejects mixed-role registrations and exposes `currentRole`. `AndroidServiceHost` reads `currentRole` on `onStartCommand` and calls the API-34 three-arg `startForeground(id, notification, types)` with the role-derived foreground-service-type bitmask. Manifest declares the union (`camera|microphone|mediaPlayback`).
- `DefaultNetworkClientRepository` instantiates `ForegroundServiceLink(AppRole.CLIENT)`, starts it on `connect()` and stops it on `disconnect()`.
- The control `WebSocketConnectionHandler` switches to `reconnect = true`. `WebSocketConnectionHandler.disconnect()` is fixed to cancel the pending retry coroutine. A new `ConnectionState.Reconnecting(server)` is set on control drop instead of tearing down the whole client. The audio/video receivers ride out the gap on their own existing `reconnect = true` because `serverSelectionDataSource` is no longer cleared.
- UI cleanup removes the `SideEffect`-based reconnect trigger, the `Reconnect` action, the dead `NetworkClientRepository.reconnect()`, and `ServerSelectionDataSource.lastServerId`.

**Tech Stack:** Kotlin Multiplatform (Android + Desktop/JVM), Ktor WebSockets, Koin DI, Compose, MVVM.

**Spec corrections / notes applied during planning:**

- The project has no test source sets (per `AGENTS.md`). Verification is by build: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`. There is no per-task TDD step.
- The spec's UI line says the `Reconnecting` branch should reuse the existing disconnected overlay with a "Reconnecting…" copy. The existing string `client_disconnected_reconnecting` reads "Disconnected. Reconnecting...", which is wrong for the new state. A new string `client_reconnecting` is added in Task 8.
- Spec §3 says `onControlConnectionClosed` "only updates `connectionState` to `Reconnecting(serverId)`". This is the literal behavior implemented here. Note: if the handler's single retry attempt also fails (e.g. WiFi still down at retry time), the user can be left in `Reconnecting` indefinitely and must press Disconnect from the overlay to escape. This matches "Open risks" item 2 in the spec.
- All references to `NetworkState` in the spec map to `ConnectionState` post-rebase (rename was done in commit 1346754 and earlier branch work).

---

## File Structure

**Create:** none.

**Modify:**

- `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidService.kt` — add `val role: AppRole`.
- `network/common/src/androidMain/kotlin/org/vpilo/babymonitor/network/common/ForegroundServiceLink.android.kt` — supply `role` on the anonymous service.
- `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceRegistry.kt` — reject mixed-role registrations, expose `currentRole`.
- `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceHost.kt` — call the three-arg `startForeground` with role-derived type bitmask.
- `androidService/src/androidMain/AndroidManifest.xml` — add `mediaPlayback` to the service-type list, add `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission.
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt` — add `ForegroundServiceLink(AppRole.CLIENT)`; start at top of `connect()`, stop at end of `disconnect()`; pass `reconnect = true` to control handler; rework `onControlConnectionClosed`; remove `reconnect()` impl.
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/WebSocketConnectionHandler.kt` — track `retryJob` and cancel it from `disconnect()`.
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ConnectionState.kt` — add `Reconnecting(server: ServerId)`.
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt` — remove `reconnect()` from the interface.
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ServerSelectionDataSource.kt` — remove `lastServerId`.
- `appCommon/src/commonMain/composeResources/values/strings.xml` — add `client_reconnecting`.
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreen.kt` — drop `SideEffect` reconnect trigger; render overlay for `Reconnecting` and `Disconnected`.
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenAction.kt` — remove `Reconnect`.
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenViewModel.kt` — remove the `Reconnect` action handler.

---

### Task 1: Add `role: AppRole` to `AndroidService` and supply it from `ForegroundServiceLink`

Compile-only change. Adds a property to the interface and provides it from the only existing implementation.

**Files:**
- Modify: `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidService.kt`
- Modify: `network/common/src/androidMain/kotlin/org/vpilo/babymonitor/network/common/ForegroundServiceLink.android.kt`

- [ ] **Step 1: Add `role` to `AndroidService`**

Edit `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidService.kt`. Add the import and the property:

```kotlin
package org.vpilo.babymonitor.android.service

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.vpilo.babymonitor.model.AppRole

/**
 * Interface for services on Android which need to stay active when the app is not in the foreground.
 */
interface AndroidService {
    val role: AppRole

    fun onServiceStarted(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    )

    fun onServiceStopped()
}
```

- [ ] **Step 2: Supply `role` from `ForegroundServiceLink.android.kt`**

Edit `network/common/src/androidMain/kotlin/org/vpilo/babymonitor/network/common/ForegroundServiceLink.android.kt`. Capture the constructor `role` argument and expose it on the anonymous `AndroidService`:

```kotlin
package org.vpilo.babymonitor.network.common

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.model.AppRole

/**
 * Android [ForegroundServiceLink] to keep components alive in the background.
 *
 * This keeps the app's foreground service alive for the duration of the network components' availability, so connections and streaming
 * can keep running while the device is locked.
 */
actual class ForegroundServiceLink actual constructor(
    role: AppRole,
) {
    private val service =
        object : AndroidService {
            override val role: AppRole = role

            override fun onServiceStarted(
                context: Context,
                lifecycleOwner: LifecycleOwner,
            ) = Unit

            override fun onServiceStopped() = Unit
        }

    actual fun start() {
        AndroidServiceRegistry.register(service)
    }

    actual fun stop() {
        AndroidServiceRegistry.unregister(service)
    }
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidService.kt \
        network/common/src/androidMain/kotlin/org/vpilo/babymonitor/network/common/ForegroundServiceLink.android.kt
git commit -m "Add role to AndroidService"
```

---

### Task 2: Mixed-role guard and `currentRole` getter in `AndroidServiceRegistry`

`AndroidServiceRegistry` becomes role-aware: rejects registration of a service whose `role` differs from already-registered services, and exposes a `currentRole` for the host to read.

**Files:**
- Modify: `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceRegistry.kt`

- [ ] **Step 1: Add `currentRole` and the mixed-role guard**

Edit `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceRegistry.kt`. Replace the file with:

```kotlin
package org.vpilo.babymonitor.android.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

object AndroidServiceRegistry : KoinComponent {
    private val services: MutableSet<WeakReference<AndroidService>> = mutableSetOf()

    private var serviceInstance: LifecycleService? = null

    private val scope = CoroutineScope(get<CoroutineContext>())

    val isServiceRunning: Boolean
        get() = serviceInstance != null

    val currentRole: AppRole
        get() =
            synchronized(services) {
                services.firstNotNullOfOrNull { it.get()?.role } ?: AppRole.UNDECIDED
            }

    fun register(service: AndroidService) {
        synchronized(services) {
            if (services.any { it.get() == service }) {
                Logger.w(TAG) { "Service $service is already registered" }
                return
            }

            val existingRole = services.firstNotNullOfOrNull { it.get()?.role }
            check(existingRole == null || existingRole == service.role) {
                "Cannot register service with role ${service.role}; existing services use $existingRole"
            }

            services.add(WeakReference(service))
        }

        if (services.size == 1) {
            Logger.d(TAG) { "Requesting service start" }
            val context: Context = get()
            val intent = Intent(context, AndroidServiceHost::class.java)
            context.startForegroundService(intent)
        } else {
            serviceInstance?.let {
                scope.launch {
                    service.onServiceStarted(it, it)
                }
            }
        }
    }

    fun unregister(service: AndroidService) {
        if (services.none { it.get() == service }) {
            Logger.w(TAG) { "Service $service is not registered" }
            return
        }

        synchronized(services) {
            services.removeIf { with(it.get()) { this == service || this == null } }
        }

        if (services.isEmpty()) {
            Logger.d(TAG) { "Requesting service stop" }
            val context: Context = get()
            val intent = Intent(context, AndroidServiceHost::class.java)
            context.stopService(intent)
        } else {
            serviceInstance?.let {
                scope.launch {
                    service.onServiceStopped()
                }
            }
        }
    }

    internal fun reportServiceStarted(service: LifecycleService) {
        serviceInstance = service

        synchronized(services) {
            services.forEach { it.get()?.onServiceStarted(service, service) }
        }
    }

    internal fun reportServiceStopped() {
        serviceInstance = null

        synchronized(services) {
            services.forEach { it.get()?.onServiceStopped() }
        }
    }

    private val TAG = AndroidServiceRegistry::class
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceRegistry.kt
git commit -m "Make AndroidServiceRegistry role-aware"
```

---

### Task 3: `AndroidServiceHost` calls three-arg `startForeground`; manifest declares the union

`AndroidServiceHost.onStartCommand` reads `AndroidServiceRegistry.currentRole`, converts it to the foreground-service-type bitmask, and calls `startForeground(id, notification, types)`. Manifest is updated with `mediaPlayback` and the matching permission.

**Files:**
- Modify: `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceHost.kt`
- Modify: `androidService/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Update `AndroidServiceHost.onStartCommand`**

Edit `androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceHost.kt`. Add the imports and update `onStartCommand`:

```kotlin
package org.vpilo.babymonitor.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import androidx.core.app.NotificationCompat
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole

/**
 * Empty service to keep the app running in the background while the camera is active.
 */
internal class AndroidServiceHost : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.d(TAG) { "Created service" }
        AndroidServiceRegistry.reportServiceStarted(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Logger.d(TAG) { "Starting service in foreground" }
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification(), foregroundServiceType())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG) { "Destroyed service" }
        AndroidServiceRegistry.reportServiceStopped()
    }

    private fun foregroundServiceType(): Int =
        when (val role = AndroidServiceRegistry.currentRole) {
            AppRole.SERVER -> FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE
            AppRole.CLIENT -> FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            AppRole.UNDECIDED -> error("Foreground service started with no registered role")
        }

    private fun createNotificationChannel() {
        val serviceChannel =
            NotificationChannel(
                CHANNEL_ID,
                "Camera active",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Baby Monitor")
            .setContentText("Camera is recording.")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()

    companion object {
        private val TAG = AndroidServiceHost::class

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "BackgroundServiceChannel"
    }
}
```

- [ ] **Step 2: Update the manifest**

Edit `androidService/src/androidMain/AndroidManifest.xml`. Replace contents with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.camera"
        android:required="true" />

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application>
        <service
            android:name=".AndroidServiceHost"
            android:foregroundServiceType="camera|microphone|mediaPlayback" />
    </application>
</manifest>
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add androidService/src/androidMain/kotlin/org/vpilo/babymonitor/android/service/AndroidServiceHost.kt \
        androidService/src/androidMain/AndroidManifest.xml
git commit -m "Pass role-derived foreground-service-type to startForeground"
```

---

### Task 4: Wire `ForegroundServiceLink(AppRole.CLIENT)` into `DefaultNetworkClientRepository`

The client now starts a foreground service while a connection is active. This is the first end-to-end checkpoint — after this task, the client has a foreground service notification while connected.

**Files:**
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`

- [ ] **Step 1: Add `ForegroundServiceLink` to the repository**

Edit `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`. Add the imports:

```kotlin
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.network.common.ForegroundServiceLink
```

Add the field next to the other private members (after `controlHandler`):

```kotlin
private val foregroundLink = ForegroundServiceLink(AppRole.CLIENT)
```

Update `connect(serverId: ServerId)` so `foregroundLink.start()` runs at the top, before any WebSocket is opened. The new method body:

```kotlin
override suspend fun connect(serverId: ServerId) {
    val server =
        if (!serverId.isLocalServer) {
            Server(serverId, InetAddress.getByAddress(relayHost, ByteArray(4)))
        } else {
            discoveryManager.getDiscoveredServers().firstOrNull { it.id.name == serverId.name }
                ?: run {
                    Logger.w(TAG) { "Server ${serverId.name} not found in local servers." }
                    connectionState.value = ConnectionState.Disconnected(ConnectionState.ErrorReason.ServerNotFound)
                    return
                }
        }

    foregroundLink.start()
    closeAllConnections()

    Logger.i(TAG) { "Connecting to server ${serverId.name} (local: ${serverId.isLocalServer})" }
    controlHandler =
        WebSocketConnectionHandler(
            server = server,
            endpointPath = Endpoints.CONTROL,
            onDisconnected = { onControlConnectionClosed(it) },
            sessionBlock = {
                onControlConnectionOpened(server)
                controlClientWebSocket()
            },
            coroutineScope = scope,
        ).apply { connect() }

    connectionState.value = ConnectionState.Connecting(serverId)
}
```

Update `disconnect()` so `foregroundLink.stop()` runs after `closeAllConnections()`:

```kotlin
override suspend fun disconnect() {
    closeAllConnections()
    connectionState.value = ConnectionState.Disconnected(ConnectionState.ErrorReason.ClientQuit)
    foregroundLink.stop()
    Logger.i(TAG) { "Client state: ${connectionState.value}" }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt
git commit -m "Run a foreground service while the client is connected"
```

---

### Task 5: Add `ConnectionState.Reconnecting`

Add the new state. No call sites use it yet, so the addition is purely additive — `when` expressions over `ConnectionState` that don't already cover all branches will gain a (still-unused) one. The `is ConnectionState.Disconnected` checks in `ClientHomeScreen` keep compiling.

**Files:**
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ConnectionState.kt`

- [ ] **Step 1: Add `Reconnecting`**

Edit `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ConnectionState.kt`. Replace contents with:

```kotlin
package org.vpilo.babymonitor.model.repository

sealed interface ConnectionState {
    data class Disconnected(
        val reason: ErrorReason,
        val additionalInfo: Throwable? = null,
    ) : ConnectionState

    data class Connecting(
        val server: ServerId,
    ) : ConnectionState

    data class Reconnecting(
        val server: ServerId,
    ) : ConnectionState

    data class Connected(
        val server: ServerId,
    ) : ConnectionState

    enum class ErrorReason {
        ServerNotFound,
        ServerQuit,
        ClientQuit,
        NotConnectedYet,
        ConnectionFailed,
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ConnectionState.kt
git commit -m "Add ConnectionState.Reconnecting"
```

---

### Task 6: `WebSocketConnectionHandler.disconnect()` cancels the pending retry

Today the retry is launched into the shared `coroutineScope`; `disconnect()` only cancels `connectionJob`, so a retry already in `delay(...)` survives. The fix is to track the retry coroutine and cancel it from `disconnect()`. This change benefits audio and video too, which already use `reconnect = true`.

**Files:**
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/WebSocketConnectionHandler.kt`

- [ ] **Step 1: Track and cancel `retryJob`**

Edit `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/WebSocketConnectionHandler.kt`. Add the `retryJob` field next to `connectionJob`:

```kotlin
private var connectionJob: Job? = null
private var retryJob: Job? = null
private var isDisconnectionHandled = false
```

Replace the `invokeOnCompletion` block in `connect(remainingHosts:)` so the launched retry coroutine is captured into `retryJob`:

```kotlin
}.apply {
    invokeOnCompletion {
        if (isDisconnectionHandled) return@invokeOnCompletion
        Logger.w(TAG) { "Disconnection for $endpointPath" }
        isDisconnectionHandled = true
        retryJob =
            coroutineScope.launch {
                onDisconnected(it ?: CancellationException("Unhandled closure"))
                if (reconnect) {
                    Logger.i(TAG) { "Attempting to reconnect for $endpointPath" }
                    delay(Constants.RECONNECTION_TIMEOUT)
                    connect()
                }
            }
    }
}
```

Replace `disconnect()` so it cancels both jobs:

```kotlin
fun disconnect() {
    isDisconnectionHandled = true
    connectionJob?.cancel()
    connectionJob = null
    retryJob?.cancel()
    retryJob = null
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/WebSocketConnectionHandler.kt
git commit -m "Cancel pending retry on WebSocketConnectionHandler.disconnect"
```

---

### Task 7: Control handler uses `reconnect = true`; `onControlConnectionClosed` sets `Reconnecting`

After this task, a control-channel drop triggers a handler-owned reconnect attempt and the client transitions to `Reconnecting` instead of fully tearing down. The audio/video receivers ride out the gap on their own existing `reconnect = true` because `serverSelectionDataSource` is no longer cleared.

**Files:**
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`

- [ ] **Step 1: Pass `reconnect = true` to the control handler and capture `serverId` in the disconnect callback**

Edit `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`. Update the `WebSocketConnectionHandler` constructor call inside `connect(serverId:)`:

```kotlin
controlHandler =
    WebSocketConnectionHandler(
        server = server,
        endpointPath = Endpoints.CONTROL,
        onDisconnected = { onControlConnectionClosed(serverId, it) },
        sessionBlock = {
            onControlConnectionOpened(server)
            controlClientWebSocket()
        },
        coroutineScope = scope,
        reconnect = true,
    ).apply { connect() }
```

- [ ] **Step 2: Rework `onControlConnectionClosed` to set `Reconnecting`**

In the same file, replace the entire `onControlConnectionClosed` method with:

```kotlin
private fun onControlConnectionClosed(serverId: ServerId, exception: Throwable) {
    Logger.i(TAG) { "Control connection closed: ${exception.prettify()}" }
    connectionState.value = ConnectionState.Reconnecting(serverId)
    Logger.i(TAG) { "Client state: ${connectionState.value}" }
}
```

Remove imports that this file no longer references:

- `java.net.ConnectException`
- `java.net.SocketException`
- `javax.net.ssl.SSLException`
- `kotlinx.coroutines.CancellationException`
- `kotlinx.coroutines.channels.ClosedReceiveChannelException`

Keep `org.vpilo.babymonitor.common.ktx.prettify` — it is used in the new log statement.

- [ ] **Step 3: Build to verify**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt
git commit -m "Auto-reconnect the control channel; surface Reconnecting state"
```

---

### Task 8: Render the overlay for `Reconnecting`; remove the redundant `SideEffect`

The disconnected overlay covers both `Disconnected` and `Reconnecting`. The `SideEffect` reconnect trigger goes away; reconnect is now handler-owned.

**Files:**
- Modify: `appCommon/src/commonMain/composeResources/values/strings.xml`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreen.kt`

- [ ] **Step 1: Add the `client_reconnecting` string resource**

Edit `appCommon/src/commonMain/composeResources/values/strings.xml`. Add a new string entry next to `client_disconnected_reconnecting`:

```xml
<string name="client_reconnecting">Reconnecting…</string>
```

- [ ] **Step 2: Update `ClientHomeScreen`**

Edit `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreen.kt`.

Remove the `SideEffect` import (`androidx.compose.runtime.SideEffect`) and the `SideEffect { ... }` block from `ClientHomeScreen`. The block currently lives between `NavigationBackHandler(...)` and the `val title = ...` line — drop it entirely.

In the same file, add an import for the new string:

```kotlin
import babymonitor.appcommon.generated.resources.client_reconnecting
```

In `ClientHomeScreenContent`, replace the disconnected-overlay block (the `if (connectionState is ConnectionState.Disconnected) { Surface { ... } }` block) with a version that handles both states and picks the copy based on the state:

```kotlin
val overlayMessage =
    when (connectionState) {
        is ConnectionState.Disconnected -> stringResource(Res.string.client_disconnected_reconnecting)
        is ConnectionState.Reconnecting -> stringResource(Res.string.client_reconnecting)
        else -> null
    }
if (overlayMessage != null) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
    ) {
        Backdrop {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(overlayMessage)
                Button(
                    modifier = Modifier.padding(top = Theme.Paddings.Medium),
                    onClick = onDisconnected,
                ) {
                    Text(stringResource(Res.string.client_disconnect))
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add appCommon/src/commonMain/composeResources/values/strings.xml \
        appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreen.kt
git commit -m "Show overlay for Reconnecting; drop UI-driven reconnect trigger"
```

---

### Task 9: Remove dead code — `Reconnect` action, repo `reconnect()`, `lastServerId`

After Task 7+8 nothing calls these. Removing them keeps the repository surface honest.

**Files:**
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenAction.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenViewModel.kt`
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ServerSelectionDataSource.kt`

- [ ] **Step 1: Remove `Reconnect` from `ClientHomeScreenAction`**

Edit `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenAction.kt`. Replace contents with:

```kotlin
package org.vpilo.babymonitor.app.client.home

sealed interface ClientHomeScreenAction {
    data object ToggleAudio : ClientHomeScreenAction

    data object ToggleVideo : ClientHomeScreenAction
}
```

- [ ] **Step 2: Remove the `Reconnect` handler from `ClientHomeScreenViewModel`**

Edit `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenViewModel.kt`. Replace the `onAction` body with:

```kotlin
override fun onAction(action: ClientHomeScreenAction) {
    vmScope.launch {
        when (action) {
            ClientHomeScreenAction.ToggleAudio -> {
                settingsRepository.save(Setting.ClientEnabledAudio, !state.isAudioPlaying)
            }

            ClientHomeScreenAction.ToggleVideo -> {
                settingsRepository.save(Setting.ClientEnabledVideo, !state.isVideoPlaying)
            }
        }
    }
}
```

- [ ] **Step 3: Remove `reconnect()` from the `NetworkClientRepository` interface**

Edit `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt`. Replace contents with:

```kotlin
package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface NetworkClientRepository {
    val connectionStateFlow: Flow<ConnectionState>

    val serverStateFlow: Flow<ServerState>

    val discoveredServerIdsFlow: Flow<Set<ServerId>>

    suspend fun connect(serverId: ServerId)

    suspend fun disconnect()

    fun setRelayHost(host: String)

    fun setDeviceName(name: String)
}
```

- [ ] **Step 4: Remove `reconnect()` from `DefaultNetworkClientRepository`**

Edit `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`. Delete this method in its entirety:

```kotlin
override suspend fun reconnect() {
    val last = serverSelectionDataSource.lastServerId
    if (last == null) {
        Logger.w(TAG) { "No server to reconnect to." }
        return
    }
    Logger.i(TAG) { "Reconnecting to ${last.name}" }
    connect(last)
}
```

- [ ] **Step 5: Remove `lastServerId` from `ServerSelectionDataSource`**

Edit `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ServerSelectionDataSource.kt`. Replace contents with:

```kotlin
package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.network.common.Server

internal class ServerSelectionDataSource {
    private val collector = MutableStateFlow<Server?>(null)
    val server: StateFlow<Server?> = collector.asStateFlow()

    fun set(server: Server?) {
        collector.value = server
    }
}
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenAction.kt \
        appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenViewModel.kt \
        model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt \
        network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt \
        network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ServerSelectionDataSource.kt
git commit -m "Remove dead Reconnect action and repository reconnect path"
```

---

## Manual verification (after all tasks)

The project has no automated test source sets. Manually verify on an Android device:

1. Install: `./gradlew :appAndroid:installDebug`
2. Run the server on a second device (or desktop) and connect from the client.
3. Confirm the foreground-service notification appears while connected (title "Baby Monitor").
4. Lock the client device for ≥ 30 seconds with audio playing. Audio should keep playing; on unlock, the camera feed should still be live (or restart promptly via `LifecycleResumeEffect`).
5. With the client connected, kill the server process briefly (or yank the network) and restore it. The client should pass through `Reconnecting` (overlay shows "Reconnecting…") and recover to `Connected` once the server is back, without manual intervention.
6. Press the Disconnect button on the overlay — client should fully disconnect (no further reconnect attempts), foreground notification should clear.
