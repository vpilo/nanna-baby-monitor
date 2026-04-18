plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":network:common"))

            implementation(libs.bundles.ktor.server)
            implementation(libs.bundles.ktor.client)
            implementation(libs.koin.core)
        }
    }
}
