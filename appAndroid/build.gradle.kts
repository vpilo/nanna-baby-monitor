import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.vpilo.babymonitor.build.gitVersionProvider

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
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

android {
    namespace = "org.vpilo.babymonitor"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    val appVersion = gitVersionProvider().get()
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // No need to strip libraries, we only use Android libraries.
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
