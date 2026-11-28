import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The watch app must be signed with the same key as the phone app, or the Data Layer will not connect them. Point
// `noterly.keyProperties` (in ~/.gradle/gradle.properties or local.properties) at the phone app's
// android/key.properties, or put a key.properties next to this project. Without one, both apps fall back to the
// local debug key, which also matches when they are built on the same PC.
val keystoreProperties = Properties().apply {
    val localProperties = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    val path = providers.gradleProperty("noterly.keyProperties").orNull ?: localProperties.getProperty("noterly.keyProperties")
    val file = path?.let { File(it) } ?: rootProject.file("key.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
        // storeFile in the phone's key.properties is relative to its android/app folder
        getProperty("storeFile")?.let { store ->
            val resolved = listOf(File(store), File(file.parentFile, "app/$store"), File(file.parentFile, store)).firstOrNull { it.exists() }
            if (resolved != null) setProperty("storeFile", resolved.absolutePath)
        }
    }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "uk.co.tdsstudios.noterly.wear"
    compileSdk = 36

    defaultConfig {
        // Same applicationId as the phone app, which the Data Layer requires
        applicationId = "uk.co.tdsstudios.noterly"
        minSdk = 36 // Wear OS 6, Galaxy Watch 8 only
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    // Matches the phone app's flavours: the GitHub release APKs of the phone app are `dev`
    flavorDimensions += "default"
    productFlavors {
        create("dev") {
            dimension = "default"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Noterly Dev")
        }
        create("production") {
            dimension = "default"
            resValue("string", "app_name", "Noterly")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":wearshared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)
    implementation(libs.wear.phone.interactions)

    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.complications.datasource)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)
}
