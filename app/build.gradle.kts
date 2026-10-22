import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingValue(environmentName: String, propertyName: String): String? =
    System.getenv(environmentName) ?: keystoreProperties.getProperty(propertyName)

val releaseStorePath = signingValue("RELEASE_KEYSTORE_PATH", "storeFile")

android {
    namespace = "com.tkleisas.multituner"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.tkleisas.multituner"
        minSdk = 29
        targetSdk = 36
        versionCode = 13
        versionName = "0.7.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    signingConfigs {
        create("release") {
            if (releaseStorePath != null) {
                storeFile = file(releaseStorePath)
                storePassword = signingValue("RELEASE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-graphics:1.7.6")
    implementation("androidx.compose.ui:ui-text:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.foundation:foundation:1.7.6")
    implementation("androidx.compose.animation:animation-core:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("com.google.oboe:oboe:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
