plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.stability.analyzer)
    alias(libs.plugins.kotlin.serialization)
    id("babymonitor.android-target")
    id("babymonitor.compose")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.app.common"

        androidResources {
            enable = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":androidService"))

            implementation(libs.androidx.activity.compose)

            implementation(libs.koin.android)
            implementation(libs.koin.androidxCompose)
        }

        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":data"))
            implementation(project(":presentation"))
            implementation(project(":camera:data"))
            implementation(project(":camera:model"))
            implementation(project(":camera:presentation"))
            implementation(project(":network:model"))
            implementation(project(":network:client"))
            implementation(project(":network:server"))
            implementation(project(":network:presentation"))
            implementation(project(":settings:data"))
            implementation(project(":settings:model"))
            implementation(project(":settings:presentation"))

            implementation(libs.compose.material)
            implementation(libs.compose.material.icons.extended)

            implementation(libs.compose.navigation)
            implementation(libs.compose.navigation.event)

            implementation(libs.jetbrains.lifecycle.runtime.compose)

            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)
            implementation(libs.koin.core)
        }
    }
}

compose.resources {
    publicResClass = true
}
