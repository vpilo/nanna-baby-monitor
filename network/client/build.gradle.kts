plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.android-target")
    id("babymonitor.compose")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.client"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":androidService"))
        }

        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":codec"))
            implementation(project(":settings:model"))
            implementation(project(":network:internal"))
            implementation(project(":network:security"))
            implementation(project(":network:model"))

            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.core)
        }
    }
}
