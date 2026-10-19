pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "noterly-watch"

include(":wearshared")

// `-Pnoterly.sharedOnly` builds and tests only the plain Kotlin module, for machines without the Android SDK or
// access to Google's Maven repository.
if (!providers.gradleProperty("noterly.sharedOnly").isPresent) {
    include(":wear")
}
