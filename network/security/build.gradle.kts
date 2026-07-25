import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.network.security"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        withHostTest {}
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
            implementation(project(":network:model"))
            implementation(project(":network:internal"))
            implementation(project(":settings:model"))

            implementation(libs.kotlinx.serialization)

            implementation(libs.ktor.websockets)

            implementation(libs.koin.core)

            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.jdk)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopTest = getByName("desktopTest")
        desktopTest.dependencies {
            implementation(libs.junit.platform)
            implementation(libs.ktor.network.tls.certificates)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
