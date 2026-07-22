import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.server"
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

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":camera:model"))
            implementation(project(":codec"))
            implementation(project(":network:common"))
            implementation(project(":network:model"))
            implementation(project(":settings:model"))

            implementation(libs.bundles.ktor.server)
            implementation(libs.bundles.ktor.client)
            implementation(libs.ktor.server.netty)

            implementation(libs.koin.core)
        }

        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.ktor.network.tls.certificates)
        }

        val androidMain = getByName("androidMain")
        androidMain.dependencies {
            implementation(libs.ktor.network.tls.certificates)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
