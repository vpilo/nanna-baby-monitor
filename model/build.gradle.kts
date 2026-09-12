plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
    id("babymonitor.android-target")
    id("babymonitor.compose")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.model"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))

            implementation(libs.compose.ui)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization)
        }

        androidMain.dependencies {
            api(libs.androidx.camera.core)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
