import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val jvmVersion =
    libs.versions.jvm.toolchain
        .get()
val androidMinSdk =
    libs.versions.android.minSdk
        .get()
        .toInt()
val androidCompileSdk =
    libs.versions.android.compileSdk
        .get()
        .toInt()

plugins.withId(
    libs.plugins.androidLibrary
        .get()
        .pluginId,
) {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(jvmVersion.toInt())

        // AGP registers the Android target as an extension of the Kotlin one, so there is no generated accessor for it here.
        with(extensions.getByName("android") as KotlinMultiplatformAndroidLibraryTarget) {
            minSdk = androidMinSdk
            compileSdk = androidCompileSdk
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
            }
        }
    }
}
