plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.android-target")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.internal"

        withHostTest {}
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":network:model"))
            implementation(project(":settings:model"))

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

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopTest = getByName("desktopTest")
        desktopTest.dependencies {
            implementation(libs.junit.platform)
        }
    }
}
