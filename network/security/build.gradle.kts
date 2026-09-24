plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
    id("babymonitor.android-target")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.security"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":network:model"))
            implementation(project(":network:internal"))
            implementation(project(":settings:model"))

            implementation(libs.kotlinx.serialization)

            implementation(libs.ktor.websockets)
            implementation(libs.ktor.network.tls.certificates)
            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.core)

            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.jdk)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopTest = getByName("desktopTest")
        desktopTest.dependencies {
            implementation(libs.junit.platform)

            implementation(libs.bundles.ktor.server)
            implementation(libs.ktor.server.netty)
        }
    }
}
