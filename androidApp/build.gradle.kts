import java.util.Properties
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun releaseKeystoreProperty(name: String): String? =
    releaseKeystoreProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val releaseKeystoreConfigured =
    releaseKeystorePropertiesFile.exists() &&
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
            .all { releaseKeystoreProperty(it) != null }

fun releaseSigningErrorMessage(): String = buildString {
    appendLine("Release signing is not configured.")
    appendLine("Copy keystore.properties.example to keystore.properties and point it at a local release keystore.")
    appendLine("Expected keys: storeFile, storePassword, keyAlias, keyPassword.")
}

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ojoclaro.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ojoclaro.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystoreConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProperty("storeFile")!!)
                storePassword = releaseKeystoreProperty("storePassword")
                keyAlias = releaseKeystoreProperty("keyAlias")
                keyPassword = releaseKeystoreProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "ASSISTANT_BASE_URL",
                "\"http://10.0.2.2:8787\""
            )
        }

        release {
            buildConfigField(
                "String",
                "ASSISTANT_BASE_URL",
                "\"\""
            )

            if (releaseKeystoreConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }

            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Fails with a clear message when release signing files are missing."
    doLast {
        if (!releaseKeystoreConfigured) {
            throw GradleException(releaseSigningErrorMessage())
        }

        val keystoreFile = rootProject.file(releaseKeystoreProperty("storeFile")!!)
        if (!keystoreFile.exists()) {
            throw GradleException(
                buildString {
                    appendLine("Release keystore not found at: ${keystoreFile.absolutePath}")
                    appendLine("Generate it with scripts/create_release_keystore.ps1 and keep keystore.properties local.")
                }
            )
        }
    }
}

tasks.matching { task ->
    task.name == "assembleRelease" ||
        task.name == "bundleRelease" ||
        task.name == "packageRelease"
}.configureEach {
    dependsOn(validateReleaseSigning)
}
