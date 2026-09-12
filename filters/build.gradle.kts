plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("babymonitor.android-target")
    id("babymonitor.desktop-target")
    id("babymonitor.detekt")
}

kotlin {
    android {
        namespace = "org.vpilo.babymonitor.filters"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":model"))
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
