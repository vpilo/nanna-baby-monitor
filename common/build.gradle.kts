import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.git-version")
    id("babymonitor.detekt")
}

val generateBuildInfo = tasks.register("generateBuildInfo") {
    description = "Generates a BuildInfo.kt file with versioning information."

    val versionInfo = gitVersion.info
    val outputDir = layout.buildDirectory.dir("generated/buildinfo/commonMain/kotlin")

    inputs.property("version", versionInfo.map { "${it.versionName}|${it.versionCore}|${it.versionCode}" })
    outputs.dir(outputDir)

    doLast {
        val info = versionInfo.get()
        val packageDir = outputDir.get().asFile.resolve("org/vpilo/babymonitor/common")
        packageDir.mkdirs()
        packageDir.resolve("BuildInfo.kt").writeText(
            """
            |package org.vpilo.babymonitor.common
            |
            |/** Generated at build time from git. Do not edit. */
            |object BuildInfo {
            |    const val VERSION: String = "${info.versionName}"
            |    const val VERSION_CORE: String = "${info.versionCore}"
            |    const val VERSION_CODE: Int = ${info.versionCode}
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.common"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)

            dependencies {
                api(libs.kotlinx.coroutines)

                implementation(libs.slf4j.simple)
            }
        }

        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            api(libs.kotlinx.coroutines.jvm)

            implementation(libs.slf4j.api)
        }

    }
}
