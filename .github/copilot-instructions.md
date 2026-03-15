# Copilot Instructions

## Project Details

At runtime the app will have a role, either as camera or monitor.
When a camera, the app captures audio and video from camera and microphone, shows the video stream locally, and sends them to any
connected monitor role apps.
When a monitor, the app receives encoded audio and video data from the network, then decodes and plays them.
A Desktop app and an Android app can stream audio and video to each other.

## Gradle Build Tasks

This is a Kotlin Multiplatform project using `androidLibrary` and `jvm("desktop")` targets.

**Compile a module's source sets:**

- Desktop: `:module:compileKotlinDesktop`
- Android: `:module:compileAndroidMain`

**Examples:**

```
./gradlew :codec:compileKotlinDesktop
./gradlew :codec:compileAndroidMain
```

> Note: There is no `compileDebugKotlinAndroid` task. The Android target uses `compileAndroidMain`.
