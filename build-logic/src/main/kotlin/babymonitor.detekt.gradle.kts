import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.detekt)
}

private val detektTaskName = "detekt"

fun String.uppercaseFirst() = replaceFirstChar { it.uppercase() }

detekt {
    config.setFrom(rootProject.files("config/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom(fileTree("src") { include("*/kotlin/**") })
}

tasks.withType<Detekt>().configureEach {
    // Ignore generated code, mostly Compose resource accessors.
    exclude { "/build/generated/" in it.file.invariantSeparatorsPath }
}

tasks.withType<Detekt>()
    .matching { it.name.startsWith("detektMetadata") }
    .configureEach { enabled = false }

plugins.withId(libs.plugins.kotlinMultiplatform.get().pluginId) {
    val detektTasks = tasks.withType<Detekt>()

    // With type resolution on, each compilation task's own source set is used by Detekt, and not commonMain.
    // This adds all source sets to all compilation tasks, so we scan it once per KMP target instead.
    extensions.configure<KotlinMultiplatformExtension> {
        targets.configureEach {
            val taskPrefix = detektTaskName + name.uppercaseFirst()
            compilations.configureEach {
                val compilation = this
                val taskName = taskPrefix + name.uppercaseFirst()
                detektTasks.matching { it.name == taskName }.configureEach {
                    setSource(compilation.allKotlinSourceSets.map { it.kotlin.srcDirs })
                }
            }
        }
    }

    // From https://jadarma.github.io/blog/posts/2025/04/convenient-detekt-conventions/
    tasks.named<Detekt>(detektTaskName) {
        setSource(project.files())
        dependsOn(detektTasks.matching { it.name != detektTaskName })
    }
}

// Ensure Detekt finds compile classpaths for Android to ensure type resolution is used.
plugins.withId(libs.plugins.androidApplication.get().pluginId) {
    val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()
    androidComponents.onVariants(androidComponents.selector().all()) { variant ->
        tasks.named<Detekt>(detektTaskName) {
            @Suppress("UnstableApiUsage")
            classpath.from(variant.compileClasspath)
        }
    }
}

plugins.withId(libs.plugins.ktlint.get().pluginId) {
    // Enforce running detekt after ktlint.
    val ktlintFormat = tasks.named("ktlintFormat")
    tasks.withType<Detekt>().configureEach {
        mustRunAfter(ktlintFormat)
    }
    tasks.register("tidy") {
        description = "Runs ktlint and detekt together on the whole project."
        group = "verification"
        dependsOn(ktlintFormat, tasks.named(detektTaskName))
    }
}
