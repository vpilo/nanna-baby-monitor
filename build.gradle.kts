import org.jetbrains.compose.internal.utils.registerOrConfigure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    base
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.stability.analyzer) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.asProvider())
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.contains("build") }
        }
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

// Add build-logic tests to the rest.
val testTask = tasks.registerOrConfigure<Task>("test") {
    description = "Runs all tests, including build-logic ones."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
    // Test tasks are named per target (`desktopTest`, `testAndroidHostTest`, ...) and live in the
    // subprojects, so collect them by type: the live collections resolve once those are evaluated.
    dependsOn(subprojects.map { it.tasks.withType<AbstractTestTask>() })
}
tasks.check {
    dependsOn(testTask)
}
