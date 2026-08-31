# AGENTS.md

## Project Overview

Baby Monitor is a **Kotlin Multiplatform** app (Android + Desktop/JVM) for streaming audio and video between devices. At runtime the app
assumes one of two roles:

- **Server (Camera):** Captures audio/video, and shows the video on the screen as a viewfinder. If and only if any clients connect, the app
  starts encoding the audio and video, and streams them to the client via WebSockets.
- **Client (Monitor):** Discovers servers via mDNS, connects, receives encoded streams, decodes, and plays them.

Key features:

- Video is always shown upright, regardless of device orientation.
- Server and clients can be Desktop or Android, in any combination.

## Architecture

- **`build-logic`** - Included build holding the `babymonitor.*` convention plugins: the git-derived versioning, the JavaCPP target
  platform, and the detekt setup. Its classes only reach a module that applies one of its plugins.
- **`common`** - Platform-agnostic logger (`Logger.d/i/w/e`), common dependency propagation.
- **`model`** - Domain types, repository interfaces, flow typealiases (`AudioFrameFlow`, `StreamingVideoFlow`), and `SharedResourceHolder`
  base class.
- **`data`** - Generic use repository implementations (e.g. `DefaultAppRoleRepository`).
- **`codec`** - `expect/actual` audio/video encoder/decoder. Desktop uses FFmpeg (JavaCPP/bytedeco); Android uses platform MediaCodec.
  Bundles the FFmpeg natives of one platform only, the host's unless `-Pjavacpp.platform=<classifier>` says otherwise.
- **`camera:model`** - Capture repository interfaces (`VideoCaptureRepository`, `AudioCaptureRepository`).
- **`camera:data`** - `expect/actual` data sources. Desktop uses `webcam-capture`; Android uses CameraX.
- **`camera:presentation`** - Viewfinder composables (`PanningVideoFeed` and its `expect/actual` platform content), the QR scanner, and
  the `expect/actual` camera/microphone permission helpers.
- **`filters`** - Stream filters applied between capture and encoding (`AudioStreamFilter`, `AudioSilenceFilter`).
- **`network:model`** - Network domain types, repository interfaces, wire constants (`Constants`, `Endpoints`, `RelaySignals`),
  device transport-string codecs (`asTransportString`/`fromTransportString`), the `PairingQrPayload`, the relay settings
  (`Setting.RelayHost`/`Setting.RelayPassphrase`) and `RelayConfiguration`.
- **`network:internal`** - Internal network plumbing shared by server and client: mDNS discovery, active-session ledger, frame codecs,
  foreground-service link. Compile-time dependency of `network:server`/`network:client`/`network:security` only.
- **`network:security`** - Pairing, session and relay-access crypto: ECDH/HKDF/AEAD/PBKDF2, the handshakes, the cipher facade,
  `DefaultPairingStorageRepository`, the sealed-frame protocol, and `relayWss`/`verifyRelayAccess`. Compile-time dependency of
  `network:server`/`network:client`/`appRelay` only.
- **`network:server`** - Ktor server with the WebSocket endpoints of `Endpoints` (`/control`, `/audio`, `/video`, `/pair`). Encodes &
  streams.
- **`network:client`** - Ktor client connecting to WebSocket endpoints, directly or through the relay's `/relay/**` ones. Receives &
  decodes.
- **`network:presentation`** - `expect/actual` local-network permission helpers.
- **`appRelay`** - Standalone relay that proxies opaque frames between camera and monitor when they are on different networks.
  Configured only through `relay.conf` in `getSettingsDir()`; gates every endpoint on the relay access handshake.
- **`androidService`** - Android-only foreground service infrastructure for background capture.
- **`presentation`** - Common Composables for the Compose UI elements, theming.
- **`settings:model`** - Settings data types and repository interfaces, plus the `expect/actual` platform helpers (`getSettingsDir()`,
  `getCurrentPlatform()`, `isSupportedOnCurrentPlatform`).
- **`settings:data`** - DataStore-backed settings persistence (`DefaultSettingsRepository`), common code only.
- **`settings:presentation`** - Settings screen Composables.
- **`appCommon`** - Shared Compose UI, navigation (`Route` sealed interface), ViewModels, Koin initialization.
- **`appDesktop` / `appAndroid`** - Thin platform entry points.

### Key Patterns

- **`expect/actual` declarations** - Used for platform-specific implementations in `codec`, `camera:data`, `camera:presentation`,
  `network:internal`, `network:presentation`, `settings:model`, `data`, `presentation`, `appCommon` and `common`. When adding
  platform-specific code, provide declarations in `commonMain` and implementations in `androidMain` + `desktopMain`. Modules declaring
  `expect` classes add the `-Xexpect-actual-classes` compiler flag.
- **`SharedResourceHolder<T>`** - Base class for resources that auto-start/stop based on subscriber count (see
  `model/.../SharedResourceHolder.kt`). Subclass it and implement `start()`/`stop()`. The `collector` `MutableSharedFlow` drives the
  lifecycle via the `reactor()` extension.
- **Flow typealiases** - Domain flows are typealiased in `model/` (e.g. `AudioFrameFlow = Flow<AudioFrame>`,
  `MutableStreamingVideoFlow = MutableSharedFlow<EncodedVideoStreamChunk>`). Use these instead of raw `Flow` types.
- **DI with Koin** - Each module exposes a `val ...KoinModule: Module` (e.g. `cameraDataKoinModule`, `networkServerKoinModule`). All are
  composed in `appCommon/.../KoinInitialization.kt`. Platform-specific modules use `expect val appPlatformModule: Module` in `AppModule.kt`.
- **MVVM** - ViewModels use Jetbrains `lifecycle-viewmodel`. State is exposed via `StateFlow` with `SharingStarted.WhileSubscribed()`.
  Actions are modeled as sealed interfaces named after their screen (e.g. `ServerHomeScreenAction`, `CameraSelectionScreenAction`).
- **Navigation** - Type-safe Compose Navigation with `Route` sealed interface using `@Serializable` data objects.

## Build & Compile

KMP project with `androidLibrary` and `jvm("desktop")` targets.

```sh
# Compile a single module
./gradlew :codec:compileKotlinDesktop     # Desktop
./gradlew :codec:compileAndroidMain       # Android (do NOT use the compileDebugKotlinAndroid task)

# Run the desktop app
./gradlew :appDesktop:run

# Build Android APK
./gradlew :appAndroid:assembleDebug
```

When building to verify changes, since this is a small app, it's quickest to just build the entire project instead of building modules
individually over multiple iterations:

```sh
./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug
```

## Conventions

- **Package root:** `org.vpilo.babymonitor`
- **Logging:** Use `Logger.d(TAG) { "message" }` from `common` module. TAG is typically the class's `KClass` reference. Declare TAG in the
  companion object of each class: `companion object { private val TAG = MyClass::class }`, or as a top level string for anything else
  needing logging.
- **Companion objects:** Should be at the bottom of a class and made private unless necessary.
- **Dependencies:** Managed via version catalog at `gradle/libs.versions.toml`. Use `libs.` references in `build.gradle.kts`.
- **Tests:** only `network:security` (crypto primitives and handshakes), `network:model` (pairing QR payload and PIN) and `build-logic`
  (version and platform derivation) have them.
  Run with `./gradlew :network:security:desktopTest :network:model:desktopTest :build-logic:test`.
