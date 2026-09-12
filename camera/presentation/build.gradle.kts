plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.stability.analyzer)
    id("babymonitor.android-target")
    id("babymonitor.compose")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.camera.presentation"

        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":presentation"))
            implementation(project(":camera:model"))
            implementation(project(":settings:model"))

            implementation(libs.compose.material)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)
            implementation(libs.koin.core)
            implementation(libs.zxing)
        }
        androidMain.dependencies {
            implementation(libs.androidx.camera.compose)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.lifecycle)
        }
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.webcam.capture)
            implementation(libs.zxing.jvm)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
