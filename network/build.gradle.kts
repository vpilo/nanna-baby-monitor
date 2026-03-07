import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidLibrary {
        namespace = "org.vpilo.babymonitor.network"
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()

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

            implementation(libs.bundles.ktor.client)
            implementation(libs.bundles.ktor.server)

            implementation(libs.koin.core)
        }
    }
}
