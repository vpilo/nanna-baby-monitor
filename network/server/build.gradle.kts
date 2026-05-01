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
        val desktopMain by getting

        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":camera:model"))
            implementation(project(":codec"))
            implementation(project(":network:common"))

            implementation(libs.bundles.ktor.server)
            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.core)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
