import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.android.service"
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

    sourceSets {
        androidMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))

            api(libs.jetbrains.lifecycle.common)

            implementation(libs.koin.android)
            implementation(libs.guava)
        }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        remove(commonTest.get())
    }
}
