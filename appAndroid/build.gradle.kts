import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    id("babymonitor.git-version")
    id("babymonitor.detekt")
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

    val appVersion = gitVersion.info.get()
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
            // Multiple ktor-server-netty transitive Netty jars each ship their own copy; irrelevant at runtime.
            excludes += "/META-INF/INDEX.LIST"
            // Every Netty artifact ships an identical copy for diagnostics; any one of them is fine to keep.
            pickFirsts += "/META-INF/io.netty.versions.properties"
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
