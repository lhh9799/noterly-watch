// Plugins go on the root build script classpath so the Kotlin plugin can see the Android plugin. The Android plugin is
// left out with -Pnoterly.sharedOnly, see settings.gradle.kts.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        if (!providers.gradleProperty("noterly.sharedOnly").isPresent) {
            classpath(libs.android.gradle.plugin)
            classpath(libs.kotlin.compose.plugin)
        }
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.serialization.plugin)
    }
}
