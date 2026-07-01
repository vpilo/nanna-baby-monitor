import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.vpilo.babymonitor.build.gitVersionProvider

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            implementation(project(":appCommon"))
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.vpilo.babymonitor.app.MainKt"

        val appVersion = gitVersionProvider().get()
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "org.vpilo.babymonitor"
            packageVersion = appVersion.versionCore
        }
    }
}
