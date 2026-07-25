# AGENTS.md

## Project Overview

Baby Monitor is a **Kotlin Multiplatform** app (Android + Desktop/JVM) for streaming audio and video between devices. At runtime the app
assumes one of two roles:

- **Server (Camera):** Captures audio/video, and shows the video on the screen as a viewfinder. If and only if any clients connect, the app
  starts encodeing the audio and video, and streams them to the client via WebSockets.
- **Client (Monitor):** Discovers servers via mDNS, connects, receives encoded streams, decodes, and plays them.

Key features:

- Video is always shown upright, regardless of device orientation.
- Server and clients can be Desktop or Android, in any combination.

## Architecture

- **`common`** — Platform-agnostic logger (`Logger.d/i/w/e`), common dependency propagation.
- **`model`** — Domain types, repository interfaces, flow typealiases (`CameraFrameFlow`, `StreamingVideoFlow`), and `SharedResourceHolder`
  base class.
- **`data`** — Generic use repository implementations (e.g. `DefaultAppRoleRepository`).
- **`codec`** — `expect/actual` audio/video encoder/decoder. Desktop uses FFmpeg (JavaCPP/bytedeco); Android uses platform MediaCodec.
- **`camera:model`** — Capture repository interfaces (`VideoCaptureRepository`, `AudioCaptureRepository`).
- **`camera:data`** — `expect/actual` data sources. Desktop uses `webcam-capture`; Android uses CameraX.
- **`network:model`** — Network domain types, repository interfaces, wire constants (`Constants`, `Endpoints`, `RelaySignals`),
  device transport-string codecs (`asTransportString`/`fromTransportString`), and the `PairingQrPayload`.
- **`network:internal`** — Internal network plumbing shared by server and client: mDNS discovery, active-session ledger, frame codecs,
  relay HTTP client, foreground-service link. Compile-time dependency of `network:server`/`network:client` only (was `network:common`).
- **`network:security`** — Pairing and session crypto: ECDH/HKDF/AEAD, pairing/session handshakes, the cipher facade,
  `DefaultPairingRepository`, and the sealed-frame protocol. Compile-time dependency of `network:server`/`network:client` only.
- **`network:server`** — Ktor server with WebSocket endpoints (`/audio`, `/video`). Encodes & streams.
- **`network:client`** — Ktor client connecting to WebSocket endpoints. Receives & decodes.
- **`androidService`** — Android-only foreground service infrastructure for background capture.
- **`presentation`** — Common Composables for the Compose UI elements, theming.
- **`settings:model`** — Settings data types and repository interfaces.
- **`settings:data`** — `expect/actual` data sources for settings persistence.
- **`settings:presentation`** — Settings screen Composables.
- **`appCommon`** — Shared Compose UI, navigation (`Route` sealed interface), ViewModels, Koin initialization.
- **`appDesktop` / `appAndroid`** — Thin platform entry points.

### Key Patterns

- **`expect/actual` classes** — Used for platform-specific implementations in `codec`, `camera:data`, `model` (e.g. `CameraFrame`), and
  `common`. When adding platform-specific code, provide declarations in `commonMain` and implementations in `androidMain` + `desktopMain`.
  Modules using this add `-Xexpect-actual-classes` compiler flag.
- **`SharedResourceHolder<T>`** — Base class for resources that auto-start/stop based on subscriber count (see
  `model/.../SharedResourceHolder.kt`). Subclass it and implement `start()`/`stop()`. The `collector` `MutableSharedFlow` drives the
  lifecycle via the `reactor()` extension.
- **Flow typealiases** — Domain flows are typealiased in `model/` (e.g. `CameraFrameFlow = Flow<CameraFrame>`,
  `MutableStreamingVideoFlow = MutableSharedFlow<EncodedVideoStreamChunk>`). Use these instead of raw `Flow` types.
- **DI with Koin** — Each module exposes a `val ...KoinModule: Module` (e.g. `cameraDataKoinModule`, `networkServerKoinModule`). All are
  composed in `appCommon/.../KoinInitialization.kt`. Platform-specific modules use `expect val appPlatformModule: Module` in `AppModule.kt`.
- **MVVM** — ViewModels use Jetbrains `lifecycle-viewmodel`. State is exposed via `StateFlow` with `SharingStarted.WhileSubscribed()`.
  Actions are modeled as sealed classes (e.g. `AppUiFlowAction`, `ClientConnectionChooserAction`).
- **Navigation** — Type-safe Compose Navigation with `Route` sealed interface using `@Serializable` data objects.

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
- **No tests exist yet** — the project has no test source sets.
