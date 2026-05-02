import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.asProvider())
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.contains("build") }
        }
    }

    configure<DetektExtension> {
        config.setFrom(rootProject.files("config/detekt.yml"))
        buildUponDefaultConfig = true
        allRules = false
        source.setFrom(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/desktopMain/kotlin",
        )
    }
}

tasks.register<Copy>("installGitHook") {
    // Install only in the root checkout, not in a worktree.
    onlyIf { File(rootProject.rootDir, ".git").isDirectory }
    description = "Installs the pre-commit git hook for ktlint and detekt checks."
    group = "git hooks"
    from("${rootProject.rootDir}/config/git-pre-commit")
    into("${rootProject.rootDir}/.git/hooks/")
    rename("git-pre-commit", "pre-commit")
    filePermissions {
        unix("rwxr-xr-x")
    }
}

tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn("installGitHook")
}
