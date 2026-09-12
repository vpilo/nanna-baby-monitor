import dev.panuszewski.gradle.pluginMarker
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    version.set(libs.versions.ktlint.asProvider())
    filter {
        exclude { "build/" in it.file.invariantSeparatorsPath }
    }
}

detekt {
    config.setFrom(files("../config/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<Detekt>().configureEach {
    exclude { "build/" in it.file.invariantSeparatorsPath }
    mustRunAfter(tasks.named("ktlintFormat"))
}

tasks.register("tidy") {
    description = "Runs ktlint and detekt together."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.named("ktlintFormat"), tasks.named("detekt"))
}

dependencies {
    compileOnly(pluginMarker(libs.plugins.androidApplication))
    compileOnly(pluginMarker(libs.plugins.androidLibrary))
    compileOnly(libs.kotlin.gradle.plugin)

    testImplementation(kotlin("test"))
}

tasks
    .withType<Test>()
    .configureEach {
        useJUnitPlatform()
    }
