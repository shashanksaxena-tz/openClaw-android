import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
    id("com.google.devtools.ksp")
}

// Load version from properties file
val versionProps = Properties()
val versionFile = file("version.properties")
if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}

android {
    namespace = "com.openclaw.android"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.openclaw.android"
        minSdk = 29
        targetSdk = 35
        versionCode = versionProps.getProperty("VERSION_CODE", "10").toInt()
        versionName = "${versionProps.getProperty("VERSION_MAJOR", "1")}.${versionProps.getProperty("VERSION_MINOR", "0")}.${versionProps.getProperty("VERSION_PATCH", "0")}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Build for common Android architectures
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // LiteRT-LM is a pure Maven dependency — no native build needed.
    // The old externalNativeBuild/cmake and ndkVersion config has been removed.

    // Extract native libs to disk so LiteRT-LM can load them directly.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// No native submodule initialization needed — LiteRT-LM is a pure Maven dependency

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    // Activity & Navigation
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room for conversation history
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Swipe gestures
    implementation("me.saket.swipe:swipe:1.3.0")

    // Runtime permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Encrypted storage for API keys
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // LiteRT-LM — Google's on-device LLM inference engine
    // Replaces the old llama.cpp JNI/C++ native build entirely
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
