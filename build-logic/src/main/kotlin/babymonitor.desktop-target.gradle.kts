import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val jvmVersion =
    libs.versions.jvm.toolchain
        .get()

plugins.withId(
    libs.plugins.kotlinMultiplatform
        .get()
        .pluginId,
) {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(jvmVersion.toInt())
        jvm("desktop")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
