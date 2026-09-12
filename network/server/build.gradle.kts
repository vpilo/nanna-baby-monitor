plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.android-target")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.server"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":camera:model"))
            implementation(project(":codec"))
            implementation(project(":network:internal"))
            implementation(project(":network:security"))
            implementation(project(":network:model"))
            implementation(project(":settings:model"))

            implementation(libs.bundles.ktor.server)
            implementation(libs.bundles.ktor.client)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.network.tls.certificates)

            implementation(libs.koin.core)
        }
    }
}
