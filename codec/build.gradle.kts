import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    id("babymonitor.detekt")
    id("babymonitor.javacpp-platform")
}

// Bytedeco splits each library into two bindings+native jars per platform.
// Take only the natives of the platform being built to make smaller releases.
val nativePlatform = javacppPlatform.classifier.get()
val javacppNatives = dependencies.variantOf(libs.javacpp) { classifier(nativePlatform) }
val ffmpegNatives = dependencies.variantOf(libs.ffmpeg) { classifier(nativePlatform) }

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.codec"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))

            implementation(libs.compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core)
        }
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.javacpp)
            implementation(libs.ffmpeg)
            runtimeOnly(javacppNatives)
            runtimeOnly(ffmpegNatives)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
