import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.camera.data"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":camera:model"))
            implementation(project(":settings:model"))

            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(project(":androidService"))

            implementation(libs.androidx.core)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.impl)
            implementation(libs.androidx.camera.lifecycle)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.webcam.capture)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
