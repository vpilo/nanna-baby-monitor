# Camera GPU pipeline (Android) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android server's CPU-heavy capture+encode pipeline with CameraX `Preview` + `VideoCapture` stream sharing + a `MediaCodec` surface-input encoder. Zero per-frame byte copy on the sender side. Desktop side moves to ImageBitmap-based `VideoSource` with sws-scaled BGRA → YUV420P encoding (review fix P4-A pulled into scope).

**Architecture:** `VideoSource` (expect/actual, lives in `:model`) bridges CameraX outputs to consumers. Android publishes two `SurfaceRequest` flows (preview + encoder); `CameraXViewfinder` consumes one, `VideoEncoder` fulfills the other with `MediaCodec.createInputSurface()`. One `bindToLifecycle` per service lifetime. Desktop unchanged in lifecycle; encoder switches from `CameraFrame`/BGR24 to `ImageBitmap`/BGRA.

**Tech Stack:** KMP (Android + Desktop/JVM), Kotlin coroutines, Koin, Compose 1.10, CameraX 1.6.1 (`androidx.camera:camera-compose` + `camera-video`), Android MediaCodec (Surface input), FFmpeg via JavaCPP (Desktop, unchanged framework, new pixel format).

---

## Spec reference

`docs/superpowers/specs/2026-05-11-camera-gpu-pipeline-android-design.md`.

## Scope check

Single subsystem (server-side capture+encode+viewfinder). One plan is the right granularity.

## File structure

**MODIFY (existing):**
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/VideoSource.kt` — drop `frameCounter` from expect.
- `model/src/androidMain/kotlin/org/vpilo/babymonitor/model/VideoSource.android.kt` — rewrite (single-slot → two `Flow<SurfaceRequest>`).
- `model/src/desktopMain/kotlin/org/vpilo/babymonitor/model/VideoSource.desktop.kt` — `frameCounter` becomes Desktop-actual-only field (drop `actual`).
- `camera/data/build.gradle.kts` — add `libs.androidx.camera.video`.
- `camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/di/CameraDataKoinModule.kt` — add `singleOf(::VideoSource)`.
- `camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.kt` — expect class signature change.
- `camera/data/src/androidMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.android.kt` — full rewrite (Preview + VideoCapture binding, SR posting).
- `camera/data/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.desktop.kt` — emit `ImageBitmap` to `VideoSource` instead of `CameraFrame` flow; lifecycle from `videoSource.isActive`.
- `camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/CameraVideoCaptureRepository.kt` — expose `videoSource`, drop `frames`.
- `camera/model/src/commonMain/kotlin/org/vpilo/babymonitor/camera/model/VideoCaptureRepository.kt` — `frames` → `videoSource`.
- `camera/presentation/build.gradle.kts` — add `libs.androidx.camera.compose` to androidMain.
- `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.kt` — takes `source: VideoSource` directly; no ViewModel, no Koin lookup; common shell + `expect fun PreviewSurface`.
- `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/di/CameraPresentationKoinModule.kt` — drop `viewModelOf(::CameraViewFinderViewModel)`.
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreenViewModel.kt` — inject `VideoCaptureRepository` (private; presence forces construction), expose `videoSource`.
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreen.kt` — pass `viewModel.videoSource` to `CameraViewFinder`.
- `codec/src/commonMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.kt` — constructor param `input` → `source: VideoSource`.
- `codec/src/androidMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.android.kt` — full rewrite (surface-input mode).
- `codec/src/desktopMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.desktop.kt` — read `ImageBitmap` from `videoSource.frames`; sws-scale BGRA → YUV420P; remove `fillSourceFrame` switch.
- `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/NetworkVideoSenderRepository.kt` — pass `videoSource` to `VideoEncoder`.

**CREATE:**
- `camera/presentation/src/androidMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.android.kt` — `actual fun PreviewSurface` hosts `CameraXViewfinder`.
- `camera/presentation/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.desktop.kt` — `actual fun PreviewSurface` hosts `PannableImage` + `FpsCounter`.

**DELETE (stage 3 for the ViewModel; stage 4 for the rest):**
- `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinderViewModel.kt` — replaced by direct parameter passing.
- `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/ktx/CameraFrameKtx.kt`
- `camera/presentation/src/androidMain/kotlin/org/vpilo/babymonitor/camera/presentation/ktx/CameraFrameKtx.android.kt`
- `camera/presentation/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/presentation/ktx/CameraFrameKtx.desktop.kt`
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/CameraFrame.kt`
- `model/src/androidMain/kotlin/org/vpilo/babymonitor/model/CameraFrame.android.kt`
- `model/src/desktopMain/kotlin/org/vpilo/babymonitor/model/CameraFrame.desktop.kt`
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/CameraFrameFlow.kt`

## No automated tests

This project has no test source sets. Verification at each stage is `./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`. Manual UAT happens at Stage 5.

---

## Stage 1 — Define the `VideoSource` API

Production code doesn't reference `VideoSource` yet, so each task in this stage lands with a green build.

### Task 1.1: Confirm scope assumption

- [ ] **Step 1: Grep for `VideoSource` references**

Run: `grep -rn "VideoSource" --include="*.kt" --include="*.kts" /home/vale/projects/babym | grep -v "/build/"`

Expected: only three lines, in `model/src/{commonMain,androidMain,desktopMain}/.../VideoSource*.kt`. Anything else means a consumer exists that the rest of Stage 1 would silently break — stop and reconcile before proceeding.

### Task 1.2: Rewrite `VideoSource` expect class

**Files:**
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/VideoSource.kt`

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.model

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific source of camera frames consumed by the video encoder
 * and (on Android) by the viewfinder.
 *
 * On Android: publishes two [android.view.SurfaceRequest] flows; the
 * viewfinder collects [previewSurfaceRequest] and the encoder collects
 * [encoderSurfaceRequest].
 *
 * On Desktop: publishes a flow of [androidx.compose.ui.graphics.ImageBitmap]
 * frames consumed by both viewfinder and encoder.
 */
expect class VideoSource() {
    /**
     * Whether the encoder is actively producing frames (= the encoder
     * SurfaceRequest has been fulfilled on Android, or there is at least
     * one frame subscriber on Desktop).
     */
    val isActive: Flow<Boolean>
}
```

### Task 1.3: Rewrite `VideoSource.android`

**Files:**
- Modify: `model/src/androidMain/kotlin/org/vpilo/babymonitor/model/VideoSource.android.kt`

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.model

import androidx.camera.core.SurfaceRequest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

actual class VideoSource actual constructor() {
    private val _previewSurfaceRequest =
        MutableSharedFlow<SurfaceRequest>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val _encoderSurfaceRequest =
        MutableSharedFlow<SurfaceRequest>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** SurfaceRequests for the CameraX Preview UseCase. Consumer = the viewfinder Composable. */
    val previewSurfaceRequest: Flow<SurfaceRequest> = _previewSurfaceRequest

    /** SurfaceRequests for the CameraX VideoCapture UseCase. Consumer = the encoder. */
    val encoderSurfaceRequest: Flow<SurfaceRequest> = _encoderSurfaceRequest

    /**
     * True whenever at least one consumer is collecting either SurfaceRequest flow.
     * Drives the camera lifecycle: the camera controller registers itself as an
     * AndroidService when this flips true and unregisters when it flips false.
     */
    actual val isActive: Flow<Boolean> =
        combine(
            _previewSurfaceRequest.subscriptionCount,
            _encoderSurfaceRequest.subscriptionCount,
        ) { preview, encoder -> preview > 0 || encoder > 0 }
            .distinctUntilChanged()

    /** Called by the CameraX controller when Preview issues a SurfaceRequest. */
    fun postPreviewSurfaceRequest(request: SurfaceRequest) {
        _previewSurfaceRequest.tryEmit(request)
    }

    /** Called by the CameraX controller when VideoCapture issues a SurfaceRequest. */
    fun postEncoderSurfaceRequest(request: SurfaceRequest) {
        _encoderSurfaceRequest.tryEmit(request)
    }
}
```

### Task 1.4: Update `VideoSource.desktop`

**Files:**
- Modify: `model/src/desktopMain/kotlin/org/vpilo/babymonitor/model/VideoSource.desktop.kt`

Drop `actual` from `frameCounter` (no longer in expect). Keep everything else.

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.model

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

actual class VideoSource actual constructor() {
    private val _frames =
        MutableSharedFlow<ImageBitmap>(
            replay = 0,
            extraBufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val _frameCounter = MutableStateFlow(0L)

    val frames: SharedFlow<ImageBitmap> = _frames.asSharedFlow()

    /** Desktop-only: drives the viewfinder FpsCounter. */
    val frameCounter: StateFlow<Long> = _frameCounter.asStateFlow()

    actual val isActive: Flow<Boolean> =
        _frames.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()

    fun emit(bitmap: ImageBitmap) {
        _frames.tryEmit(bitmap)
        _frameCounter.value += 1
    }

    fun reset() {
        _frameCounter.value = 0L
    }
}
```

### Task 1.5: Build verification

- [ ] **Step 1: Run build**

Run: `cd /home/vale/projects/babym && ./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`

Expected: `BUILD SUCCESSFUL`. If `androidx.camera.core.SurfaceRequest` fails to resolve, check that `:model`'s androidMain depends on `libs.androidx.camera.core` (it already does; verify).

If failing, fix and re-run before continuing.

### Task 1.6: Commit

- [ ] **Step 1: Stage and commit**

```bash
cd /home/vale/projects/babym
git add model/src/commonMain/kotlin/org/vpilo/babymonitor/model/VideoSource.kt \
        model/src/androidMain/kotlin/org/vpilo/babymonitor/model/VideoSource.android.kt \
        model/src/desktopMain/kotlin/org/vpilo/babymonitor/model/VideoSource.desktop.kt
git commit -m "$(cat <<'EOF'
refactor(model): reshape VideoSource for SurfaceRequest publication

Android actual now publishes two SharedFlow<SurfaceRequest> (replay=1)
- one for Preview, one for VideoCapture - that consumers collect and
fulfill. Replaces the single Surface-slot draft.

frameCounter is dropped from the common interface (preview frames
never cross app code on Android with the upcoming GPU pipeline); it
remains as a Desktop-actual-only field driving the Desktop viewfinder
FpsCounter.

No production code references VideoSource yet, so the build stays
green through this change.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Stage 2 — CameraX dependencies + Koin singleton

### Task 2.1: Verify version catalog already has the libraries

- [ ] **Step 1: Confirm libs.androidx.camera.video and libs.androidx.camera.compose exist**

Run: `grep -E "androidx-camera-(video|compose)" /home/vale/projects/babym/gradle/libs.versions.toml`

Expected: two lines, both referencing version `androidx-camera` (currently `1.6.1`). If missing, add them under the existing `androidx-camera-*` block — both libraries follow the standard `androidx.camera:camera-{video,compose}` coordinates.

### Task 2.2: Add `camera-video` dependency to `:camera:data`

**Files:**
- Modify: `camera/data/build.gradle.kts`

- [ ] **Step 1: Add `libs.androidx.camera.video` to androidMain.dependencies**

Add the line after `implementation(libs.androidx.camera.lifecycle)` in the `androidMain.dependencies { ... }` block:

```kotlin
implementation(libs.androidx.camera.video)
```

### Task 2.3: Add `camera-compose` dependency to `:camera:presentation`

**Files:**
- Modify: `camera/presentation/build.gradle.kts`

- [ ] **Step 1: Inspect current state**

Run: `grep -n "androidMain\|camera" /home/vale/projects/babym/camera/presentation/build.gradle.kts`

Expected: identify the `androidMain.dependencies { ... }` block. If absent, add it; if present, locate where to insert the new line.

- [ ] **Step 2: Add `libs.androidx.camera.compose`**

In the `androidMain.dependencies { ... }` block:

```kotlin
implementation(libs.androidx.camera.compose)
```

If the block does not yet exist, add:

```kotlin
androidMain.dependencies {
    implementation(libs.androidx.camera.compose)
}
```

### Task 2.4: Register `VideoSource` as a Koin singleton

**Files:**
- Modify: `camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/di/CameraDataKoinModule.kt`

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.camera.data.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.camera.data.CameraVideoCaptureRepository
import org.vpilo.babymonitor.camera.data.MicrophoneAudioCaptureRepository
import org.vpilo.babymonitor.camera.model.AudioCaptureRepository
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.VideoSource

val cameraDataKoinModule: Module =
    module {
        single { VideoSource() }
        singleOf(::MicrophoneAudioCaptureRepository)
            .bind<AudioCaptureRepository>()
        singleOf(::CameraVideoCaptureRepository)
            .bind<VideoCaptureRepository>()
    }
```

### Task 2.5: Build verification

- [ ] **Step 1: Run build**

Run: `cd /home/vale/projects/babym && ./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`

Expected: `BUILD SUCCESSFUL`. If `libs.androidx.camera.video` or `libs.androidx.camera.compose` fails to resolve, recheck `gradle/libs.versions.toml`.

### Task 2.6: Commit

- [ ] **Step 1: Stage and commit**

```bash
cd /home/vale/projects/babym
git add camera/data/build.gradle.kts \
        camera/presentation/build.gradle.kts \
        camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/di/CameraDataKoinModule.kt
git commit -m "$(cat <<'EOF'
build(camera): add CameraX video + compose deps, register VideoSource singleton

- camera/data picks up androidx.camera:camera-video for the
  VideoCapture UseCase.
- camera/presentation picks up androidx.camera:camera-compose for
  CameraXViewfinder.
- VideoSource registered as a Koin single so the data source, encoder,
  and viewfinder all share the same instance.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Stage 3 — Pipeline switch (single atomic stage, single commit)

Stage 3 swaps the entire capture+encode+viewfinder API. The intermediate build state will not compile until all tasks land; verify only at Task 3.12. The single commit groups everything together.

### Task 3.1: Update `VideoCaptureRepository` interface

**Files:**
- Modify: `camera/model/src/commonMain/kotlin/org/vpilo/babymonitor/camera/model/VideoCaptureRepository.kt`

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.camera.model

import org.vpilo.babymonitor.model.VideoSource

/**
 * Repository for the camera capture pipeline.
 * Exposes a [VideoSource] consumed by both the viewfinder and the encoder.
 */
interface VideoCaptureRepository {
    val videoSource: VideoSource
}
```

### Task 3.2: Update `VideoCaptureDataSource` expect

**Files:**
- Modify: `camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.kt`

The data source now receives the `VideoSource` via constructor and drives it. It no longer exposes a frames flow.

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.model.VideoSource

internal expect class VideoCaptureDataSource(videoSource: VideoSource) {
    fun setResolution(resolution: CameraResolution)
}
```

### Task 3.3: Update `CameraVideoCaptureRepository`

**Files:**
- Modify: `camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/CameraVideoCaptureRepository.kt`

The repository takes `VideoSource` from Koin and passes it to the data source.

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.camera.model.settings.CameraResolution
import org.vpilo.babymonitor.model.VideoSource
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import kotlin.coroutines.CoroutineContext

class CameraVideoCaptureRepository(
    override val videoSource: VideoSource,
    settingsRepository: SettingsRepository,
    coroutineContext: CoroutineContext,
) : VideoCaptureRepository {
    private val dataSource: VideoCaptureDataSource = VideoCaptureDataSource(videoSource)

    init {
        settingsRepository
            .flowOf(Setting.CameraResolution)
            .onEach { dataSource.setResolution(it) }
            .launchIn(CoroutineScope(coroutineContext))
    }
}
```

### Task 3.4: Rewrite `VideoCaptureDataSource.android`

**Files:**
- Modify: `camera/data/src/androidMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.android.kt`

Full rewrite. Drops `ImageAnalysis` + NV12 conversion + `SharedResourceHolder<CameraFrame>`. Binds `Preview` + `VideoCapture` once for the service lifetime; pushes SurfaceRequests into the `VideoSource`.

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.camera.data

import android.content.Context
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import androidx.annotation.MainThread
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaSpec
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoOutput
import androidx.camera.video.VideoOutput.SourceState
import androidx.camera.video.VideoSpec
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.VideoSource
import java.lang.ref.WeakReference
import kotlin.math.abs

internal actual class VideoCaptureDataSource(
    private val videoSource: VideoSource,
    private val mainDispatcher: CoroutineDispatcher,
) : AndroidService {
    actual constructor(videoSource: VideoSource) : this(videoSource, Dispatchers.Main)

    override val role: AppRole = AppRole.SERVER

    private var cameraProvider: ProcessCameraProvider? = null
    private var serviceContext: Context? = null
    private var serviceLifecycleOwner: LifecycleOwner? = null

    private val executor = mainDispatcher.asExecutor()

    private var resolution: CameraResolution = CameraResolution.Medium

    private var preview: Preview? = null
    private var videoCapture: VideoCapture<EncoderVideoOutput>? = null

    private val resolutionSelector: ResolutionSelector
        get() {
            val size =
                when (resolution) {
                    CameraResolution.Low -> Size(640, 480)
                    CameraResolution.Medium -> Size(1280, 720)
                    CameraResolution.High -> Size(1920, 1080)
                }
            return ResolutionSelector
                .Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build()
        }

    private var orientationListener: OrientationEventListener? = null

    @MainThread
    private fun bind(
        cameraProvider: ProcessCameraProvider,
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        val selector = resolutionSelector

        val preview =
            Preview
                .Builder()
                .setResolutionSelector(selector)
                .build()
                .also {
                    it.surfaceProvider = Preview.SurfaceProvider { request ->
                        videoSource.postPreviewSurfaceRequest(request)
                    }
                }

        val videoCapture =
            VideoCapture
                .Builder(EncoderVideoOutput())
                .setResolutionSelector(selector)
                .build()

        val cameraSelector =
            CameraSelector
                .Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

        val supportedFpsRanges =
            cameraProvider
                .getCameraInfo(cameraSelector)
                .supportedFrameRateRanges
                .sortedBy { it.upper }
        val desiredFpsRange =
            when (resolution) {
                CameraResolution.Low -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_LOW_QUALITY)
                CameraResolution.Medium -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_MEDIUM_QUALITY)
                CameraResolution.High -> Range(CameraConstants.MIN_FPS, CameraConstants.MAX_FPS_HIGH_QUALITY)
            }
        val fpsRange =
            desiredFpsRange
                .takeIf { supportedFpsRanges.contains(it) }
                ?: supportedFpsRanges
                    .map { it to abs(it.lower - desiredFpsRange.lower + it.upper - desiredFpsRange.upper) }
                    .also {
                        Logger.w(TAG) {
                            "FPS range $desiredFpsRange not supported, selecting one from: ${it.joinToString(", ")}"
                        }
                    }.minBy { it.second }
                    .first
        Logger.i(TAG) { "Selected FPS range: $fpsRange" }

        val sessionConfig =
            SessionConfig(
                useCases = listOf(preview, videoCapture),
                frameRateRange = fpsRange,
            )

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, sessionConfig)
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            Logger.e(TAG) { "Failed to bind camera: ${ex.prettify()}" }
            return
        }

        this.preview = preview
        this.videoCapture = videoCapture

        orientationListener?.disable()
        orientationListener =
            object : OrientationEventListener(context) {
                init {
                    enable()
                }

                private var lastRotation = ORIENTATION_UNKNOWN
                private val previewRef = WeakReference(preview as UseCase)
                private val videoRef = WeakReference(videoCapture as UseCase)

                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN || orientation == lastRotation) return
                    lastRotation = orientation
                    val rotation = UseCase.snapToSurfaceRotation(orientation)
                    val p = previewRef.get()
                    val v = videoRef.get()
                    if (p == null && v == null) {
                        this.disable()
                        return
                    }
                    p?.setTargetRotation(rotation)
                    v?.setTargetRotation(rotation)
                }
            }
    }

    actual fun setResolution(resolution: CameraResolution) {
        if (this.resolution == resolution) return
        this.resolution = resolution
        val provider = cameraProvider
        val context = serviceContext
        val owner = serviceLifecycleOwner
        if (provider != null && context != null && owner != null) {
            Logger.i(TAG) { "Resolution changed to $resolution, rebinding camera" }
            CoroutineScope(mainDispatcher).launch { bind(provider, context, owner) }
        }
    }

    override fun onServiceStarted(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        serviceContext = context
        serviceLifecycleOwner = lifecycleOwner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                CoroutineScope(mainDispatcher).launch {
                    bind(provider, context, lifecycleOwner)
                }
            },
            executor,
        )
    }

    override fun onServiceStopped() {
        CoroutineScope(mainDispatcher).launch {
            cameraProvider?.unbindAll()
            cameraProvider = null
            preview = null
            videoCapture = null
            orientationListener?.disable()
            orientationListener = null
            serviceContext = null
            serviceLifecycleOwner = null
        }
    }

    private val lifecycleScope = CoroutineScope(mainDispatcher)

    init {
        // Drive AndroidService registration off VideoSource.isActive.
        // - isActive flips true (the viewfinder or the encoder subscribed) → register self.
        //   AndroidServiceRegistry starts the foreground service if it wasn't running, or
        //   fires onServiceStarted immediately (else branch) if ForegroundServiceLink-SERVER
        //   already kept it alive.
        // - isActive flips false (nobody is collecting either SurfaceRequest flow) →
        //   unregister. onServiceStopped fires, CameraX unbinds. If foregroundLink-SERVER
        //   is still registered, the foreground service stays alive but the camera is
        //   released — exactly the long-tail idle state we want.
        lifecycleScope.launch {
            videoSource.isActive.collect { active ->
                if (active) {
                    AndroidServiceRegistry.register(this@VideoCaptureDataSource)
                } else {
                    AndroidServiceRegistry.unregister(this@VideoCaptureDataSource)
                }
            }
        }
    }

    /**
     * Minimal [VideoOutput] that forwards the SurfaceRequest into the shared [VideoSource].
     *
     * CameraX uses [MediaSpec] / [StreamInfo] to negotiate format with [Recorder]; for a
     * raw-surface consumer we publish a static no-codec MediaSpec and a single fixed
     * stream id. Adjust if CameraX 1.6.x rejects this minimal contract — the spec's risk
     * register flags this as something to verify.
     */
    private inner class EncoderVideoOutput : VideoOutput {
        private val mediaSpec = MediaSpec.builder().setVideoSpec(VideoSpec.builder().build()).build()
        private val streamInfo =
            androidx.camera.video.StreamInfo.of(
                /* id = */ androidx.camera.video.StreamInfo.STREAM_ID_ANY,
                /* streamState = */ androidx.camera.video.StreamInfo.StreamState.ACTIVE,
            )

        override fun onSurfaceRequested(request: SurfaceRequest) {
            videoSource.postEncoderSurfaceRequest(request)
        }

        override fun getMediaSpec() = androidx.camera.core.impl.Observable.constant(mediaSpec)
        override fun getStreamInfo() = androidx.camera.core.impl.Observable.constant(streamInfo)
        override fun onSourceStateChanged(state: SourceState) {
            Logger.d(TAG) { "VideoOutput source state: $state" }
        }
    }

    private companion object {
        private val TAG = VideoCaptureDataSource::class
    }
}
```

> Note: `Observable.constant` / `MediaSpec` / `StreamInfo` are CameraX internal types; the executor must verify the exact API shape against `androidx.camera.video:1.6.1` and `androidx.camera.core.impl:1.6.1`. If `StreamInfo.of` / `Observable.constant` don't exist with these signatures, consult the AndroidX source at `androidx.camera.video.VideoOutput` and `androidx.camera.core.impl.Observable` and use their public factory methods. The spec's risk register (item 1) flags this as a known unknown.

### Task 3.5: Rewrite `VideoCaptureDataSource.desktop`

**Files:**
- Modify: `camera/data/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.desktop.kt`

Drop `SharedResourceHolder<CameraFrame>`. Drive lifecycle from `videoSource.isActive` (= subscriber count on `videoSource.frames`). Emit `ImageBitmap`s.

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.camera.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.vpilo.babymonitor.camera.data.ktx.sizes
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.VideoSource
import java.awt.Dimension
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

internal actual class VideoCaptureDataSource(
    private val videoSource: VideoSource,
    webcamGetter: () -> Webcam,
) {
    actual constructor(videoSource: VideoSource) : this(videoSource, { Webcam.getDefault() })

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var videoCaptureJob: Job? = null
    private var resolution: CameraResolution = CameraResolution.Medium
    private val webcam: Webcam = webcamGetter()

    init {
        videoSource.isActive
            .distinctUntilChanged()
            .onEach { active -> if (active) start() else stop() }
            .let { flow -> coroutineScope.launch { flow.collect { } } }
    }

    private fun start() {
        if (videoCaptureJob?.isActive == true) {
            Logger.w(TAG) { "Camera is already running, ignoring start request." }
            return
        }

        val resolutions = resolutionsFor(resolution)
        @Suppress("SpreadOperator")
        webcam.setCustomViewSizes(*resolutions)
        for (size in resolutions) {
            webcam.setViewSize(size)
            if (webcam.open()) {
                Logger.d(TAG) { "Camera opened with ${size.sizes}" }
                break
            }
        }
        if (!webcam.isOpen) {
            webcam.setCustomViewSizes(null)
            check(webcam.open()) { "Failed to open webcam with any resolution." }
        }

        Logger.d(TAG) {
            "Camera supports resolutions: ${webcam.viewSizes.map { it.sizes }}, current ${webcam.viewSize.sizes}"
        }
        videoCaptureJob =
            coroutineScope
                .launch { frameLoop() }
                .apply {
                    invokeOnCompletion { ex ->
                        if (ex == null || ex is CancellationException) {
                            Logger.d(TAG) { "Camera stopped" }
                        } else {
                            Logger.w(TAG, ex) { "Camera failed!" }
                        }
                        webcam.close()
                        videoCaptureJob = null
                    }
                }
        Logger.i(TAG) { "Camera started" }
    }

    private fun stop() {
        runBlocking { videoCaptureJob?.cancelAndJoin() }
        videoCaptureJob = null
    }

    actual fun setResolution(resolution: CameraResolution) {
        if (this.resolution == resolution) return
        this.resolution = resolution
        if (videoCaptureJob?.isActive == true) {
            Logger.i(TAG) { "Resolution changed to $resolution, restarting capture" }
            stop()
            start()
        }
    }

    private suspend fun frameLoop() {
        val maxFrameTime =
            (
                1000L /
                    when (resolution) {
                        CameraResolution.Low -> CameraConstants.MAX_FPS_LOW_QUALITY
                        CameraResolution.Medium -> CameraConstants.MAX_FPS_MEDIUM_QUALITY
                        CameraResolution.High -> CameraConstants.MAX_FPS_HIGH_QUALITY
                    }
            ).milliseconds

        while (webcam.isOpen) {
            val frameTime =
                measureTime {
                    if (!webcam.isImageNew) {
                        delay(5.milliseconds)
                        return@measureTime
                    }
                    val image: ImageBitmap? =
                        webcam.getImage()?.toComposeImageBitmap()
                    if (image == null) {
                        Logger.w(TAG) { "Failed to capture image" }
                        delay(100.milliseconds)
                    } else {
                        videoSource.emit(image)
                    }
                }

            val diff = frameTime - maxFrameTime
            if (diff.isNegative()) {
                delay(maxFrameTime - frameTime)
            }
        }
    }

    private companion object {
        private val TAG = VideoCaptureDataSource::class

        private val highResolutions =
            arrayOf<Dimension>(
                WebcamResolution.FHD.size,
                WebcamResolution.WUXGA.size,
                WebcamResolution.HDP.size,
                WebcamResolution.UXGA.size,
            )

        private val mediumResolutions =
            arrayOf<Dimension>(
                WebcamResolution.HD.size,
                WebcamResolution.WXGA2.size,
                WebcamResolution.SXGA.size,
                WebcamResolution.XGA.size,
            )

        private val lowResolutions =
            arrayOf<Dimension>(
                WebcamResolution.VGA.size,
                WebcamResolution.SVGA.size,
                WebcamResolution.HVGA.size,
                WebcamResolution.QVGA.size,
            )

        private fun resolutionsFor(resolution: CameraResolution): Array<Dimension> =
            when (resolution) {
                CameraResolution.Low -> lowResolutions
                CameraResolution.Medium -> mediumResolutions + lowResolutions
                CameraResolution.High -> highResolutions + mediumResolutions + lowResolutions
            }
    }
}
```

### Task 3.6: Update `VideoEncoder` expect class

**Files:**
- Modify: `codec/src/commonMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.kt`

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.codec

import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.VideoSource
import kotlin.coroutines.CoroutineContext

expect class VideoEncoder(
    source: VideoSource,
    output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    fun start()
    fun stop()
}
```

### Task 3.7: Rewrite `VideoEncoder.android`

**Files:**
- Modify: `codec/src/androidMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.android.kt`

Surface-input mode. No byte copies; only the output drain loop remains.

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import androidx.camera.core.SurfaceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.VideoSource
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

@Suppress("LoopWithTooManyJumpStatements")
actual class VideoEncoder actual constructor(
    private val source: VideoSource,
    private val output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)
    private val executor = coroutineContext.let { ctx ->
        (ctx[kotlinx.coroutines.CoroutineDispatcher.Key] ?: kotlinx.coroutines.Dispatchers.Default).asExecutor()
    }
    private var encodingJob: Job? = null

    actual fun start() {
        if (encodingJob?.isActive == true) {
            Logger.w(TAG) { "Video encoder is already running, ignoring start request." }
            return
        }
        encodingJob =
            coroutineScope.launch {
                source.encoderSurfaceRequest.collectLatest { request ->
                    runEncodeLoop(request)
                }
            }
    }

    actual fun stop() {
        encodingJob?.cancel()
        encodingJob = null
    }

    private suspend fun runEncodeLoop(request: SurfaceRequest) {
        val width = request.resolution.width
        val height = request.resolution.height
        val codec = createVideoEncoder(width, height)
        val inputSurface = codec.createInputSurface()
        codec.start()
        request.provideSurface(inputSurface, executor) { _ -> inputSurface.release() }
        try {
            drainEncoderLoop(codec)
        } finally {
            try {
                request.invalidate()
            } catch (
                @Suppress("TooGenericExceptionCaught") ex: Exception,
            ) {
                Logger.d(TAG, ex) { "SurfaceRequest already invalidated/cancelled" }
            }
            releaseCodec(codec)
        }
    }

    private suspend fun drainEncoderLoop(codec: MediaCodec) {
        var videoFrameCount = 0L
        var codecConfigData: ByteArray? = null
        val bufferInfo = MediaCodec.BufferInfo()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputIndex < 0) {
                // Periodic keyframe request based on frame count
                videoFrameCount++
                if (videoFrameCount % (MediaFormats.Video.FRAME_RATE * MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS) == 0L) {
                    codec.setParameters(keyframeRequest)
                }
                continue
            }

            val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputIndex)
            if (outputBuffer == null || bufferInfo.size <= 0) {
                codec.releaseOutputBuffer(outputIndex, false)
                continue
            }

            val data = ByteArray(bufferInfo.size)
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
            outputBuffer.get(data)

            val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

            if (isCodecConfig) {
                codecConfigData = ensureAnnexB(data)
            } else {
                val annexBData = ensureAnnexB(data)
                val emitData =
                    if (isKey && codecConfigData != null) {
                        codecConfigData!! + annexBData
                    } else {
                        annexBData
                    }
                output.tryEmit(EncodedVideoStreamChunk(data = emitData, isKeyFrame = isKey))
            }

            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    private fun createVideoEncoder(width: Int, height: Int): MediaCodec {
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, MediaFormats.Video.BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, MediaFormats.Video.FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS)
                // Surface input mode: explicitly request COLOR_FormatSurface so MediaCodec
                // accepts createInputSurface() and bypasses byte-buffer input.
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
            }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun releaseCodec(codec: MediaCodec) {
        try {
            codec.stop()
            codec.release()
        } catch (ex: IllegalStateException) {
            Logger.w(TAG, ex) { "Error releasing codec" }
        }
    }

    private companion object {
        private val TAG = VideoEncoder::class

        const val CODEC_TIMEOUT_US = 10_000L

        private val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)

        private val keyframeRequest =
            Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }

        private fun ensureAnnexB(data: ByteArray): ByteArray {
            if (data.size >= 4 &&
                data[0] == byteFalse &&
                data[1] == byteFalse &&
                (data[2] == byteTrue || data[3] == byteTrue)
            ) {
                return data
            }

            val result = java.io.ByteArrayOutputStream(data.size + 16)
            var offset = 0
            while (offset + 4 <= data.size) {
                val nalLen =
                    ((data[offset].toInt() and 0xFF) shl 24) or
                        ((data[offset + 1].toInt() and 0xFF) shl 16) or
                        ((data[offset + 2].toInt() and 0xFF) shl 8) or
                        (data[offset + 3].toInt() and 0xFF)
                offset += 4
                if (nalLen <= 0 || offset + nalLen > data.size) break
                result.write(ANNEX_B_START_CODE)
                result.write(data, offset, nalLen)
                offset += nalLen
            }
            val converted = result.toByteArray()
            return if (converted.isNotEmpty()) converted else data
        }
    }
}
```

### Task 3.8: Rewrite `VideoEncoder.desktop`

**Files:**
- Modify: `codec/src/desktopMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.desktop.kt`

Input is now `ImageBitmap` from `source.frames`. `srcFrame` becomes `AV_PIX_FMT_BGRA`. Pixels extracted via `imageBitmap.asSkiaBitmap().readPixels()`.

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.codec

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_GLOBAL_HEADER
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_make_q
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.DoublePointer
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.VideoSource
import kotlin.coroutines.CoroutineContext

@Suppress("LongMethod", "LoopWithTooManyJumpStatements")
actual class VideoEncoder actual constructor(
    private val source: VideoSource,
    private val output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var encodingJob: Job? = null

    actual fun start() {
        if (encodingJob?.isActive == true) {
            Logger.w(TAG) { "Video encoder is already running, ignoring start request." }
            return
        }

        encodingJob =
            coroutineScope.launch {
                var encoderCtx: VideoEncoderContext? = null
                try {
                    source.frames.collect { bitmap ->
                        if (!isActive) return@collect

                        val w = bitmap.width
                        val h = bitmap.height

                        if (encoderCtx == null || encoderCtx!!.width != w || encoderCtx!!.height != h) {
                            encoderCtx?.release()
                            encoderCtx = VideoEncoderContext.create(w, h)
                            Logger.d(TAG) { "Video encoder configured for ${w}x$h" }
                        }

                        encoderCtx.encode(bitmap) { chunk ->
                            output.tryEmit(chunk)
                        }
                    }
                } finally {
                    encoderCtx?.release()
                }
            }
    }

    actual fun stop() {
        encodingJob?.cancel()
        encodingJob = null
    }

    private class VideoEncoderContext private constructor(
        val width: Int,
        val height: Int,
        private val codecCtx: AVCodecContext,
        private val swsCtx: SwsContext,
        private val srcFrame: AVFrame,
        private val yuvFrame: AVFrame,
        private val packet: AVPacket,
    ) {
        private var pts = 0L

        fun encode(
            bitmap: ImageBitmap,
            emit: (EncodedVideoStreamChunk) -> Unit,
        ) {
            fillSourceFrameFromBitmap(bitmap)
            convertToYuv()

            yuvFrame.pts(pts++)

            var ret = avcodec_send_frame(codecCtx, yuvFrame)
            if (ret < 0 && ret != AVERROR_EAGAIN()) {
                Logger.w(TAG) { "avcodec_send_frame error: $ret" }
                return
            }

            while (true) {
                ret = avcodec_receive_packet(codecCtx, packet)
                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) break
                if (ret < 0) {
                    Logger.w(TAG) { "avcodec_receive_packet error: $ret" }
                    break
                }

                val data = ByteArray(packet.size())
                packet.data().get(data)

                val isKeyFrame = (packet.flags() and AV_PKT_FLAG_KEY) != 0
                emit(EncodedVideoStreamChunk(data = data, isKeyFrame = isKeyFrame))

                av_packet_unref(packet)
            }
        }

        private fun fillSourceFrameFromBitmap(bitmap: ImageBitmap) {
            // Skia bitmaps store pixels in BGRA8888 by default on Desktop.
            val pixels = bitmap.asSkiaBitmap().readPixels()
                ?: error("Failed to read pixels from ImageBitmap")
            srcFrame.data(0).put(pixels, 0, pixels.size)
        }

        private fun convertToYuv() {
            sws_scale(
                swsCtx,
                srcFrame.data(),
                srcFrame.linesize(),
                0,
                height,
                yuvFrame.data(),
                yuvFrame.linesize(),
            )
        }

        fun release() {
            avcodec_free_context(codecCtx)
            sws_freeContext(swsCtx)
            av_frame_free(srcFrame)
            av_frame_free(yuvFrame)
            av_packet_free(packet)
        }

        companion object {
            fun create(width: Int, height: Int): VideoEncoderContext {
                val codec =
                    avcodec_find_encoder_by_name("libx264")
                        ?: avcodec_find_encoder_by_name("libopenh264")
                        ?: avcodec_find_encoder(AV_CODEC_ID_H264)
                        ?: error("H.264 encoder not found.")

                val encoderName = codec.name().getString()

                val codecCtx =
                    avcodec_alloc_context3(codec).apply {
                        width(width)
                        height(height)
                        pix_fmt(AV_PIX_FMT_YUV420P)
                        time_base(av_make_q(1, MediaFormats.Video.FRAME_RATE))
                        framerate(av_make_q(MediaFormats.Video.FRAME_RATE, 1))
                        bit_rate(MediaFormats.Video.BIT_RATE.toLong())
                        gop_size(MediaFormats.Video.FRAME_RATE * MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS)
                        max_b_frames(0)
                        flags(flags() and AV_CODEC_FLAG_GLOBAL_HEADER.inv())
                    }

                val opts = AVDictionary()
                when (encoderName) {
                    "libx264" -> {
                        av_dict_set(opts, "preset", "ultrafast", 0)
                        av_dict_set(opts, "tune", "zerolatency", 0)
                    }
                    "libopenh264" -> {
                        av_dict_set(opts, "rc_mode", "bitrate", 0)
                    }
                }

                val ret = avcodec_open2(codecCtx, codec, opts)
                av_dict_free(opts)
                check(ret >= 0) { "Could not open H.264 codec ($encoderName): $ret" }

                val srcFrame =
                    av_frame_alloc().apply {
                        format(AV_PIX_FMT_BGRA)
                        width(width)
                        height(height)
                    }
                av_frame_get_buffer(srcFrame, 0)

                val yuvFrame =
                    av_frame_alloc().apply {
                        format(AV_PIX_FMT_YUV420P)
                        width(width)
                        height(height)
                    }
                av_frame_get_buffer(yuvFrame, 0)

                val swsCtx =
                    sws_getContext(
                        width,
                        height,
                        AV_PIX_FMT_BGRA,
                        width,
                        height,
                        AV_PIX_FMT_YUV420P,
                        SWS_BILINEAR,
                        null,
                        null,
                        DoublePointer(),
                    ) ?: error("Could not initialise sws_getContext")

                val packet = av_packet_alloc()

                return VideoEncoderContext(width, height, codecCtx, swsCtx, srcFrame, yuvFrame, packet)
            }
        }
    }

    private companion object {
        private val TAG = VideoEncoder::class
    }
}
```

### Task 3.9: Update `NetworkVideoSenderRepository`

**Files:**
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/NetworkVideoSenderRepository.kt`

- [ ] **Step 1: Replace file contents**

```kotlin
package org.vpilo.babymonitor.network.server

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.codec.VideoEncoder
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import kotlin.coroutines.CoroutineContext

internal class NetworkVideoSenderRepository(
    videoRepository: VideoCaptureRepository,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<EncodedVideoStreamChunk>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ),
    StreamingVideoSenderRepository {
    override val chunks: StreamingVideoFlow = collector.asSharedFlow()

    private val encoder: VideoEncoder =
        VideoEncoder(
            source = videoRepository.videoSource,
            output = collector,
            coroutineContext = coroutineContext,
        )

    override fun start() {
        encoder.start()
    }

    override fun stop() {
        encoder.stop()
    }
}
```

### Task 3.10: Delete `CameraViewFinderViewModel`; thread `VideoSource` through `ServerHomeScreenViewModel`

The Composable receives `VideoSource` as a parameter from `ServerHomeScreen`. `ServerHomeScreenViewModel` injects `VideoCaptureRepository` (privately, so Koin constructs the camera chain at VM creation time) and exposes `videoSource`.

**Files:**
- Delete: `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinderViewModel.kt`
- Modify: `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/di/CameraPresentationKoinModule.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreenViewModel.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreen.kt`

- [ ] **Step 1: Delete the ViewModel file**

```bash
git rm camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinderViewModel.kt
```

- [ ] **Step 2: Drop the Koin binding**

Open `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/di/CameraPresentationKoinModule.kt`. Remove the `viewModelOf(::CameraViewFinderViewModel)` line and its `import` line. If the module ends up empty, leave the `module { }` skeleton and the `val cameraPresentationKoinModule: Module = module { }` declaration (it's referenced from `KoinInitialization`).

- [ ] **Step 3: Update `ServerHomeScreenViewModel`**

Add `videoCaptureRepository: VideoCaptureRepository` to the constructor as the trigger to construct the camera dep chain. Expose `videoSource` for the Composable.

```kotlin
package org.vpilo.babymonitor.app.server.home

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.LastCaptureMode
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.VideoSource
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class ServerHomeScreenViewModel(
    private val server: NetworkServerRepository,
    private val settings: SettingsRepository,
    private val videoCaptureRepository: VideoCaptureRepository,
) : AppViewModel<ServerHomeScreenAction, ServerHomeScreenState, Unit>(
        initialState = ServerHomeScreenState(),
    ) {
    val videoSource: VideoSource get() = videoCaptureRepository.videoSource

    override fun SubscriptionScope.onSubscribed() {
        server.serverStateFlow.subscribe { serverState ->
            state.copy(isAvailable = serverState.isAvailable, captureMode = serverState.captureMode).update()
        }
        settings.flowOf(Setting.LastCaptureMode).subscribe {
            state.copy(captureMode = it).update()
            server.setCaptureMode(it)
        }
        settings.flowOf(Setting.DeviceName).subscribe { name ->
            server.setDeviceName(name)
        }
        settings.flowOf(Setting.RelayHost).subscribe { host ->
            server.setRelayHost(host)
        }

        vmScope.launch {
            server.setDeviceName(settings.load(Setting.DeviceName))
            server.start()
        }
    }

    override suspend fun onUnsubscribed() {
        server.stop()
    }

    override fun onAction(action: ServerHomeScreenAction) {
        when (action) {
            is ServerHomeScreenAction.CaptureModeSelected -> {
                vmScope.launch {
                    settings.save(Setting.LastCaptureMode, action.captureMode)
                    server.setCaptureMode(action.captureMode)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Update `ServerHomeScreen` to pass `videoSource` down to `CameraViewFinder`**

Two changes — the call site of `CameraViewFinder`, and the `ServerHomeContent` signature.

Replace the existing `CameraViewFinder(captureMode = ...)` call inside `ServerHomeContent` with:

```kotlin
CameraViewFinder(
    source = videoSource,
    captureMode = captureMode,
)
```

`ServerHomeContent` needs a new `videoSource: VideoSource` parameter to thread through:

```kotlin
@Composable
private fun ServerHomeContent(
    modifier: Modifier,
    isServerAvailable: Boolean,
    captureMode: CaptureMode,
    videoSource: VideoSource,
    onModeSelected: (CaptureMode) -> Unit,
) {
    // ... unchanged column/box ...
    CameraViewFinder(
        source = videoSource,
        captureMode = captureMode,
    )
}
```

And `ServerHomeScreen` calls it with `videoSource = viewModel.videoSource`:

```kotlin
ServerHomeContent(
    modifier = modifier,
    isServerAvailable = state.isAvailable,
    captureMode = state.captureMode,
    videoSource = viewModel.videoSource,
    onModeSelected = { viewModel.send(ServerHomeScreenAction.CaptureModeSelected(it)) },
)
```

Update the `ServerHomeContentPreview` to pass an inline `VideoSource()`:

```kotlin
ServerHomeContent(
    modifier = Modifier,
    isServerAvailable = true,
    captureMode = CaptureMode.AUDIO_AND_VIDEO,
    videoSource = VideoSource(),
    onModeSelected = {},
)
```

Drop the preview's `factory<VideoCaptureRepository> { ... }` + `viewModelOf(::CameraViewFinderViewModel)` Koin overrides — no longer needed. Update imports: remove `org.vpilo.babymonitor.camera.presentation.CameraViewFinderViewModel`, `org.vpilo.babymonitor.camera.model.VideoCaptureRepository`, `org.vpilo.babymonitor.model.CameraFrameFlow`, `org.koin.core.module.dsl.viewModelOf`, `kotlinx.coroutines.flow.flowOf`. Add `org.vpilo.babymonitor.model.VideoSource`.

### Task 3.11: Rewrite `CameraViewFinder` + add platform actuals

**Files:**
- Modify: `camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.kt`
- Create: `camera/presentation/src/androidMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.android.kt`
- Create: `camera/presentation/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.desktop.kt`

`CameraViewFinder` takes the `VideoSource` as a parameter — no Koin lookup, no ViewModel. The common shell handles the audio-only overlay; the actual surface rendering moves to a platform-specific `PreviewSurface` composable.

- [ ] **Step 1: Replace commonMain `CameraViewFinder.kt`**

```kotlin
package org.vpilo.babymonitor.camera.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.server_in_audio_only_mode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.VideoSource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Backdrop
import org.vpilo.babymonitor.presentation.resources.capture_audio_only
import org.vpilo.babymonitor.presentation.resources.Res as ResCommon

@Composable
fun CameraViewFinder(
    source: VideoSource,
    captureMode: CaptureMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        PreviewSurface(
            source = source,
            modifier = Modifier.fillMaxSize(),
        )
        if (captureMode == CaptureMode.AUDIO_ONLY) {
            Backdrop(modifier = Modifier.align(Alignment.Center)) {
                Image(
                    painter = painterResource(ResCommon.drawable.capture_audio_only),
                    contentDescription = stringResource(Res.string.server_in_audio_only_mode),
                )
            }
        }
    }
}

@Composable
expect fun PreviewSurface(
    source: VideoSource,
    modifier: Modifier = Modifier,
)

@Preview
@Composable
fun CameraViewFinderNormalPreview() =
    AppPreviewTheme {
        CameraViewFinder(
            source = VideoSource(),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }

@Preview
@Composable
fun CameraViewFinderAudioOnlyPreview() =
    AppPreviewTheme {
        CameraViewFinder(
            source = VideoSource(),
            captureMode = CaptureMode.AUDIO_ONLY,
        )
    }
```

- [ ] **Step 2: Create androidMain `CameraViewFinder.android.kt`**

```kotlin
package org.vpilo.babymonitor.camera.presentation

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.vpilo.babymonitor.model.VideoSource

@Composable
actual fun PreviewSurface(
    source: VideoSource,
    modifier: Modifier,
) {
    val request by source.previewSurfaceRequest.collectAsState(initial = null)
    request?.let {
        CameraXViewfinder(
            surfaceRequest = it,
            modifier = modifier,
        )
    }
}
```

- [ ] **Step 3: Create desktopMain `CameraViewFinder.desktop.kt`**

```kotlin
package org.vpilo.babymonitor.camera.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.vpilo.babymonitor.model.VideoSource
import org.vpilo.babymonitor.presentation.composables.FpsCounter
import org.vpilo.babymonitor.presentation.composables.PannableImage

@Composable
actual fun PreviewSurface(
    source: VideoSource,
    modifier: Modifier,
) {
    val frame by source.frames.collectAsState(initial = null)
    frame?.let { bitmap ->
        PannableImage(bitmap = bitmap, modifier = modifier)
        val frameCount by source.frameCounter.collectAsState()
        FpsCounter(frameKey = frameCount)
    }
}
```

### Task 3.12: Build verification

- [ ] **Step 1: Run build**

Run: `cd /home/vale/projects/babym && ./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`

Expected: `BUILD SUCCESSFUL`. Common breaks at this point:

- Unresolved references to `CameraFrame`, `CameraFrameFlow`, `toImageBitmap`, `decodedFrames`, `frames` on `VideoCaptureRepository`: each indicates a missed call site. Run `grep -rn "CameraFrame\b\|CameraFrameFlow\|toImageBitmap\|\.frames\b" /home/vale/projects/babym --include="*.kt" | grep -v "/build/"` and update each non-test reference.
- Unresolved `EncoderVideoOutput` / `VideoOutput` members: confirm against `androidx.camera.video:1.6.1` source. If `MediaSpec`, `StreamInfo`, or `Observable.constant` have changed signatures, use the public factory methods available on those classes in the actual version.
- `CameraXViewfinder` not found: confirm `libs.androidx.camera.compose` made it into `:camera:presentation` androidMain.
- `bitmap.asSkiaBitmap().readPixels()` return type: should be `ByteArray?`. If it's a different shape in the Skiko version in use, consult the version's `org.jetbrains.skia.Bitmap` source.

Fix all issues and re-run before continuing.

### Task 3.13: Commit

- [ ] **Step 1: Stage and commit**

```bash
cd /home/vale/projects/babym
git add camera/model/src/commonMain/kotlin/org/vpilo/babymonitor/camera/model/VideoCaptureRepository.kt \
        camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.kt \
        camera/data/src/commonMain/kotlin/org/vpilo/babymonitor/camera/data/CameraVideoCaptureRepository.kt \
        camera/data/src/androidMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.android.kt \
        camera/data/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/data/VideoCaptureDataSource.desktop.kt \
        codec/src/commonMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.kt \
        codec/src/androidMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.android.kt \
        codec/src/desktopMain/kotlin/org/vpilo/babymonitor/codec/VideoEncoder.desktop.kt \
        network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/NetworkVideoSenderRepository.kt \
        camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.kt \
        camera/presentation/src/androidMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.android.kt \
        camera/presentation/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/presentation/CameraViewFinder.desktop.kt \
        camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/di/CameraPresentationKoinModule.kt \
        appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreenViewModel.kt \
        appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreen.kt
# Task 3.10 step 1 deletes CameraViewFinderViewModel.kt — git rm handles staging
git commit -m "$(cat <<'EOF'
feat(camera): GPU pipeline (Android), ImageBitmap pipeline (Desktop)

Android server:
- Bind CameraX Preview + VideoCapture once for the AndroidService
  lifetime. Stream sharing fans the camera PRIV stream to both
  consumers via internal OpenGL — zero CPU-side YUV touching.
- VideoEncoder uses MediaCodec surface input
  (createInputSurface()) — no byte-buffer copies, no manual NV12
  layout, no stride/format dance.
- Encoder lifecycle stays clients-only: encoder.start() suspends on
  VideoSource.encoderSurfaceRequest until CameraX issues one and the
  network repo gates start/stop on subscriber count.
- ImageAnalysis-based capture removed; CameraFrame no longer used.

Desktop server:
- VideoSource carries ImageBitmap; VideoCaptureDataSource emits
  toComposeImageBitmap() of the webcam frame.
- VideoEncoder switches srcFrame to AV_PIX_FMT_BGRA and sws-scales
  BGRA -> YUV420P, removing the per-type fast-path switch in
  fillSourceFrame (review fix P4-A pulled into scope).

VideoSource is a Koin singleton shared by data source and encoder.
ServerHomeScreenViewModel exposes it (via VideoCaptureRepository
constructor dep) and ServerHomeScreen threads it down to
CameraViewFinder — no Koin lookup in Composables. CameraViewFinder
delegates surface rendering to an expect/actual PreviewSurface:
Android hosts CameraXViewfinder, Desktop hosts PannableImage +
FpsCounter. CameraViewFinderViewModel is deleted.

VideoSource.isActive is a combine of the two SurfaceRequest flow
subscription counts; VideoCaptureDataSource.android observes it
to register/unregister with AndroidServiceRegistry. CameraX binds
only when at least one consumer is collecting, and unbinds the
moment both flows lose their last subscriber — so a server with
no clients and screen off releases the camera entirely.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Stage 4 — Cleanup dead code

### Task 4.1: Verify `CameraFrame*` and `CameraFrameKtx*` have no remaining references

- [ ] **Step 1: Grep for residual references**

Run: `cd /home/vale/projects/babym && grep -rn "CameraFrame\b\|CameraFrameFlow\|MutableCameraFrameFlow\|CameraFrameKtx\|toImageBitmap" --include="*.kt" --include="*.kts" . | grep -v "/build/" | grep -v "makePlaceholderCameraFrame"`

Expected output: only references inside the files being deleted (`CameraFrame.kt`, `CameraFrame.android.kt`, `CameraFrame.desktop.kt`, `CameraFrameFlow.kt`, `CameraFrameKtx.kt`, `CameraFrameKtx.android.kt`, `CameraFrameKtx.desktop.kt`). `makePlaceholderCameraFrame` is an unrelated helper in `:presentation` that returns an `ImageBitmap` — not part of this cleanup.

If anything else surfaces, update those call sites first.

### Task 4.2: Delete the files

- [ ] **Step 1: `git rm` each file**

```bash
cd /home/vale/projects/babym
git rm camera/presentation/src/commonMain/kotlin/org/vpilo/babymonitor/camera/presentation/ktx/CameraFrameKtx.kt \
       camera/presentation/src/androidMain/kotlin/org/vpilo/babymonitor/camera/presentation/ktx/CameraFrameKtx.android.kt \
       camera/presentation/src/desktopMain/kotlin/org/vpilo/babymonitor/camera/presentation/ktx/CameraFrameKtx.desktop.kt \
       model/src/commonMain/kotlin/org/vpilo/babymonitor/model/CameraFrame.kt \
       model/src/androidMain/kotlin/org/vpilo/babymonitor/model/CameraFrame.android.kt \
       model/src/desktopMain/kotlin/org/vpilo/babymonitor/model/CameraFrame.desktop.kt \
       model/src/commonMain/kotlin/org/vpilo/babymonitor/model/CameraFrameFlow.kt
```

### Task 4.3: Build verification

- [ ] **Step 1: Run build**

Run: `cd /home/vale/projects/babym && ./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug`

Expected: `BUILD SUCCESSFUL`. If anything fails to resolve, re-grep (Task 4.1) — a reference was missed.

### Task 4.4: Commit

- [ ] **Step 1: Stage and commit**

```bash
cd /home/vale/projects/babym
git commit -m "$(cat <<'EOF'
chore(model,camera/presentation): drop dead CameraFrame plumbing

With the GPU pipeline on Android and the ImageBitmap pipeline on
Desktop, CameraFrame / CameraFrameFlow / CameraFrameKtx are all
unused. Deleted along with their actuals.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Stage 5 — Manual UAT

No automated tests; the user runs these on real hardware.

### Task 5.1: Build & install

- [ ] **Step 1: Build and install on a connected Android device**

Run:
```bash
cd /home/vale/projects/babym
./gradlew :appAndroid:installDebug
```

Open the Android app; switch to Server role; grant camera + microphone + notification permissions.

### Task 5.2: Functional checks

Tick each as you confirm it:

- [ ] **Viewfinder shows live camera feed when server screen is open.** (Cover-fit, rotation handled by `CameraXViewfinder`.)
- [ ] **Connect a Desktop client.** Video stream appears on client. Server viewfinder keeps showing the feed — no glitch on connect.
- [ ] **Disconnect the client.** Server viewfinder keeps streaming. Server-side CPU drops (check Android Studio Profiler — no per-frame allocations on the main/IO threads).
- [ ] **Reconnect.** Stream resumes within ~1 sec; first keyframe arrives without artifacts.
- [ ] **Background the server app.** Foreground notification persists; client still receives video.
- [ ] **Foreground the server app.** Viewfinder reattaches and shows live stream within ~1 frame; no rebind log entry.
- [ ] **Change camera resolution in settings while streaming.** Brief rebind blackout (acceptable). Both consumers reattach automatically. Client picks up new resolution.
- [ ] **Multiple connect/disconnect cycles (10x).** No memory leak. Run `adb shell dumpsys meminfo org.vpilo.babymonitor` before and after — total PSS should not grow more than ~5 MB.

### Task 5.3: Desktop sanity check

- [ ] **Step 1: Run desktop app, switch to Server role**

Run:
```bash
cd /home/vale/projects/babym
./gradlew :appDesktop:run
```

Confirm:
- [ ] Webcam viewfinder shows live feed (`PannableImage` + drag-pan).
- [ ] FpsCounter shows ≥ 25 fps.
- [ ] Android client connects and receives video.

---

## Self-review notes

- **Spec coverage:**
  - VideoSource shape (two SR flows + isActive as combine of subscription counts) — Task 1.3.
  - Frame counter dropped from common, kept on Desktop — Tasks 1.2 + 1.4.
  - Android binding (Preview + VideoCapture, one bindToLifecycle, EncoderVideoOutput) — Task 3.4.
  - AndroidService lifecycle driven by `VideoSource.isActive` — Task 3.4 init block.
  - Encoder surface-input mode — Task 3.7.
  - Resolution-change rebind — Task 3.4 (`setResolution` → `bind`).
  - Orientation listener targets both UseCases — Task 3.4.
  - CameraViewFinder receives `VideoSource` as a parameter; no ViewModel, no Koin in Composables — Task 3.11.
  - `CameraViewFinderViewModel` deleted; `ServerHomeScreenViewModel` exposes `videoSource` — Task 3.10.
  - CameraXViewfinder on Android — Task 3.11 Step 2.
  - PannableImage + FpsCounter on Desktop — Task 3.11 Step 3.
  - Desktop pipeline switched to ImageBitmap + BGRA sws-scale — Tasks 3.5 + 3.8.
  - `NetworkVideoSenderRepository` wires new encoder — Task 3.9.
  - `VideoCaptureRepository` interface change — Task 3.1.
  - `VideoSource` Koin singleton — Task 2.4.
  - CameraX deps added — Tasks 2.2 + 2.3.
  - Dead `CameraFrame` types removed — Stage 4. `CameraViewFinderViewModel` removed in Stage 3 (Task 3.10).
  - Risk register items 1 (VideoOutput minimum surface), 2 (stream sharing engagement), 3 (SurfaceRequest.invalidate semantics) flagged in inline notes; verified during manual UAT.

- **Type consistency:** `VideoSource` constructor `()` (no params) used throughout. Encoder takes `source: VideoSource` (Task 3.6) — matches all actuals (Tasks 3.7, 3.8) and the call site in `NetworkVideoSenderRepository` (Task 3.9). `VideoCaptureDataSource` constructor signature `(videoSource: VideoSource)` matches expect (Task 3.2), Android-actual (Task 3.4), Desktop-actual (Task 3.5), and the call site in `CameraVideoCaptureRepository` (Task 3.3). `PreviewSurface(source, modifier)` consistent across common-expect (Task 3.11 Step 1), Android-actual (Step 2), Desktop-actual (Step 3). `CameraViewFinder(source, captureMode, modifier)` consistent at common decl (Task 3.11 Step 1) and call sites in `ServerHomeContent` (Task 3.10 Step 4).

- **Placeholders:** None remain. One known-unknown (`VideoOutput` minimum API on CameraX 1.6.1) is flagged with inline grep-and-pattern-match instructions for the executor.

- **Lifecycle correctness.** `VideoSource.isActive` is the single source of truth for camera lifetime. It flips true when either the viewfinder Composable (collecting `previewSurfaceRequest`) or the encoder coroutine (collecting `encoderSurfaceRequest`) subscribes; false when both unsubscribe. `VideoCaptureDataSource.android`'s init observer registers/unregisters with `AndroidServiceRegistry` accordingly — no eager registration, no leaks past server shutdown, no camera held bound when the server screen is off with no client connected.
