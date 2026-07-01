import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            implementation(project(":common"))
            implementation(project(":data"))
            implementation(project(":model"))
            implementation(project(":network:common"))
            implementation(project(":settings:data"))
            implementation(project(":settings:model"))
            implementation(libs.koin.core)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.jvm)

            implementation(libs.bundles.ktor.server)
            implementation(libs.bundles.ktor.client)

            implementation(libs.ktor.server.netty)

        }
    }
}

compose.desktop {
    application {
        mainClass = "org.vpilo.babymonitor.relay.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "org.vpilo.babymonitor.relay"
            packageVersion = "1.0.0"
        }
    }
}
