import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.camera.model"
        compileSdk =
            libs.versions.android.compileSdk
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
            implementation(project(":settings:model"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.resources)
            implementation(libs.koin.core)

            api(libs.kotlinx.coroutines)
        }
    }
}
