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
            implementation(project(":network:relay"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.jvm)
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
