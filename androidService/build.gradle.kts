import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.android-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.android.service"

        androidResources {
            enable = true
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
