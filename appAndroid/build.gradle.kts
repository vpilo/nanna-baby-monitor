import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    dependencies {
            implementation(project(":model"))
            implementation(project(":data"))
            implementation(project(":presentation"))
            implementation(project(":appCommon"))

            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.ui.tooling)

            implementation(libs.koin.android)
            implementation(libs.koin.androidxCompose)
            implementation(libs.ktor.client.okhttp)

        }
}

android {
    namespace = "org.vpilo.babymonitor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.vpilo.babymonitor"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
