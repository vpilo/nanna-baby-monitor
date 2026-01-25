import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidLibrary {
        namespace = "org.vpilo.babymonitor.common"
        compileSdk = libs.versions.android.compileSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            api(libs.kotlinx.coroutines)
        }

        desktopMain.dependencies {
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)

            api(libs.kotlinx.coroutines.jvm)
        }
    }
}
