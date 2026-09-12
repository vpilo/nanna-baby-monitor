import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
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
                implementation(libs.compose.ui.tooling)
            }
        }
    }
}
