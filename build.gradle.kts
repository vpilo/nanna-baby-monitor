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
            exclude { "build/" in it.file.invariantSeparatorsPath }
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

fun List<String>.includeInTask(reference: (taskName: String) -> List<Named>) {
    forEach { taskName ->
        tasks.registerOrConfigure<Task>(taskName) {
            description = "Runs $taskName, including on build-logic."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            dependsOn(reference(taskName))
        }
    }
}

// Gradle ignores included builds when matching task names.
listOf("ktlintCheck", "ktlintFormat", "detekt", "tidy", "test")
    .includeInTask { listOf(gradle.includedBuild("build-logic").task(":$it")) }
// Ensure the test task resolves all targets.
listOf("test")
    .includeInTask { subprojects.flatMap { it.tasks.withType<AbstractTestTask>() } }
