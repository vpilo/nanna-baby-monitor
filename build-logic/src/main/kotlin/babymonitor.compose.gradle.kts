import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    // Only enable locally: this adds useless stuff to all normal builds.
    // alias(libs.plugins.compose.stability.analyzer)
}

plugins.withId(
    libs.plugins.kotlinMultiplatform
        .get()
        .pluginId,
) {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets {
            commonMain.dependencies {
                implementation(libs.compose.foundation)
                implementation(libs.compose.runtime)
                implementation(libs.compose.resources)
                implementation(libs.compose.ui)
                implementation(libs.compose.ui.tooling.preview)
            }
        }
    }
}

plugins.withId(
    libs.plugins.androidLibrary
        .get()
        .pluginId,
) {
    // Needed by Android Studio to render previews.
    dependencies {
        "androidRuntimeClasspath"(libs.compose.ui.tooling)
    }
}
