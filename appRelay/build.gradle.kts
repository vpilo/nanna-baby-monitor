import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("babymonitor.compose")
    id("babymonitor.detekt")
    id("babymonitor.git-version")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain = getByName("desktopMain")

        desktopMain.dependencies {
            implementation(project(":common"))
            implementation(project(":data"))
            implementation(project(":model"))
            implementation(project(":network:model"))
            implementation(project(":network:security"))
            implementation(project(":settings:data"))
            implementation(project(":settings:model"))
            implementation(libs.koin.core)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.jvm)

            implementation(libs.bundles.ktor.server)
            implementation(libs.bundles.ktor.client)

            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.network.tls.certificates)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.vpilo.babymonitor.relay.MainKt"

        val appVersion = gitVersion.info.get()
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "org.vpilo.babymonitor.relay"
            packageVersion = appVersion.versionCore
        }
    }
}
