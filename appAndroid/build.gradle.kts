import com.android.builder.core.BuilderConstants
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    id("babymonitor.compose")
    id("babymonitor.detekt")
    id("babymonitor.git-version")
}

val jvmVersion =
    libs.versions.jvm.toolchain
        .get()

kotlin {
    jvmToolchain(jvmVersion.toInt())

    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
        }
    }

    dependencies {
        implementation(project(":androidService"))
        implementation(project(":appCommon"))

        implementation(libs.koin.android)
        implementation(libs.koin.androidxCompose)
    }
}

val appVersion = gitVersion.info.get()
base {
    archivesName = "org.vpilo.babymonitor-${appVersion.versionCore}"
}

android {
    namespace = "org.vpilo.babymonitor"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "org.vpilo.babymonitor"

        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = appVersion.versionCode
        versionName = appVersion.versionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmVersion)
        targetCompatibility = JavaVersion.toVersion(jvmVersion)
    }

    signingConfigs {
        val keystorePath = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
        val keystoreFile = keystorePath?.let { rootProject.file(it) }
        val keystorePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
        if (keystoreFile?.exists() == true && !keystorePassword.isNullOrEmpty()) {
            create(BuilderConstants.RELEASE) {
                logger.lifecycle("Using release keystore.")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true

                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "release"
                keyPassword = keystorePassword
            }
        }
    }
    buildTypes {
        named(BuilderConstants.RELEASE) {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "release/proguard-rules-android.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // The AGP dependency blob breaks reproducible build support for F-Droid.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Multiple ktor-server-netty transitive Netty jars ship this.
            excludes += "/META-INF/INDEX.LIST"
            // Every Netty jar ships this, just keep one.
            pickFirsts += "/META-INF/io.netty.versions.properties"
        }
        // No need to strip libraries, we only use 3rd party release libraries.
        packaging {
            jniLibs {
                excludes.add("lib/**")
            }
        }
    }
}

tasks.register("generateVersionFile") {
    description = "Writes the version of this build to a file to identify a release."

    val versionInfo = gitVersion.info
    val outputFile = layout.buildDirectory.file("outputs/version.txt")

    inputs.property("version", versionInfo.map { "${it.versionName}|${it.versionCode}" })
    outputs.file(outputFile)

    doLast {
        val info = versionInfo.get()
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("versionCode=${info.versionCode}\nversionName=${info.versionName}\n")
        logger.lifecycle("Wrote version file to ${file.absolutePath}")
        logger.lifecycle("Version: ${info.versionName} (${info.versionCode})")
    }
}
