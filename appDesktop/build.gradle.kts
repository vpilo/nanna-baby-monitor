import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("babymonitor.git-version")
    id("babymonitor.detekt")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(project(":appCommon"))
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":settings:model"))
            implementation(libs.koin.core)
            implementation(compose.desktop.currentOs)
            // ProGuard needs this: optimizing Netty's optional log4j2 backend requires its whole class hierarchy even if it's not used.
            implementation(libs.log4j.api)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.vpilo.babymonitor.app.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("release/proguard-rules-desktop.pro"))
        }

        val appVersion = gitVersion.info.get()
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage, TargetFormat.Exe)

            packageName = "org.vpilo.babymonitor"
            packageVersion = appVersion.versionCore
            description = "Camera application to stream audio video to and from Android and Desktop devices"
            copyright = "© 2026 Valerio Pilo. All rights reserved."
            licenseFile.set(rootProject.file("LICENSE"))

            linux {
                menuGroup = "video"
                appCategory = "VIDEO"
                debPackageVersion = appVersion.versionCore
                debMaintainer = "maintainer@example.com"
                rpmLicenseType = "Affero GPL v3"
                rpmPackageVersion = appVersion.versionCore
            }

            windows {
                menuGroup = "Video"
                perUserInstall = true
                exePackageVersion = appVersion.versionCore
                upgradeUuid = "cba25604-c6e8-4d3b-95ef-a5f535788623"
            }
        }
    }
}
