import dev.panuszewski.gradle.pluginMarker

plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(pluginMarker(libs.plugins.androidApplication))
    compileOnly(libs.kotlin.gradle.plugin)

    testImplementation(kotlin("test"))
}

tasks
    .withType<Test>()
    .configureEach {
        useJUnitPlatform()
    }
