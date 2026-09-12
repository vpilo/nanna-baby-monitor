plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.stability.analyzer)
    id("babymonitor.android-target")
    id("babymonitor.compose")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.presentation"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.androidx.activity.compose)
        }

        commonMain.dependencies {
            implementation(project(":common"))
        }

        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
