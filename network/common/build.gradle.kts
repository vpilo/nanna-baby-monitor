import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.common"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))

            implementation(libs.ktor.websockets)
            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.core)
        }

        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.jmdns)
        }

        androidMain.dependencies {
            implementation(project(":androidService"))
        }
    }
}
