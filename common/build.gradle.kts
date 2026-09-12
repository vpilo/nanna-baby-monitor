plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.android-target")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
    id("babymonitor.git-version")
}

val generateBuildInfo =
    tasks.register("generateBuildInfo") {
        description = "Generates a BuildInfo.kt file with versioning information."

        val versionInfo = gitVersion.info
        val outputDir = layout.buildDirectory.dir("generated/buildinfo/commonMain/kotlin")

        // Debug vs release is an app-packaging concept, and this module compiles once for every target: there is no
        // variant here to read it from. So it comes from the same explicit flag that names the version, keeping the
        // two in step - anything inferred from the invocation would build the same commit differently.
        val isReleaseBuild = gitVersion.isReleaseBuild

        inputs.property("version", versionInfo.map { "${it.versionName}|${it.versionCore}|${it.versionCode}" })
        inputs.property("isReleaseBuild", isReleaseBuild)
        outputs.dir(outputDir)

        doLast {
            val info = versionInfo.get()
            val isDebug = !isReleaseBuild.get() || info.versionName.contains("SNAPSHOT")
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
            |    const val IS_DEBUG: Boolean = $isDebug
            |}
            |
                """.trimMargin(),
            )
        }
    }

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.common"
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)

            dependencies {
                api(libs.kotlinx.coroutines)
            }
        }

        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            api(libs.kotlinx.coroutines.jvm)

            implementation(libs.slf4j.api)
            runtimeOnly(libs.slf4j.simple)
        }
    }
}
