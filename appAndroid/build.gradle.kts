import com.android.builder.core.BuilderConstants
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    id("babymonitor.compose")
    id("babymonitor.detekt")
    id("babymonitor.git-version")
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
