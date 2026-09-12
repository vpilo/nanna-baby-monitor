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
        namespace = "org.vpilo.babymonitor.data"

        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":settings:model"))

            implementation(libs.koin.core)
        }
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.oshi)
        }
    }

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.get().compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}
