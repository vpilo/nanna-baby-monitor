plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
    id("babymonitor.android-target")
    id("babymonitor.compose")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.model"

        androidResources {
            enable = true
        }

        withHostTest {
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":settings:model"))

            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopTest = getByName("desktopTest")
        desktopTest.dependencies {
            implementation(libs.junit.platform)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
