plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.android-target")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.camera.data"

        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":camera:model"))
            implementation(project(":settings:model"))
            implementation(project(":filters"))

            implementation(libs.compose.ui)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(project(":androidService"))

            implementation(libs.androidx.core)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.impl)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.video)
        }
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.webcam.capture)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
