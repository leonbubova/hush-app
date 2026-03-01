import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services") apply false
    id("com.google.firebase.crashlytics") apply false
}

android {
    namespace = "com.hush.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hush.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Pass test API keys from local.properties to instrumented tests
        val localProps = rootProject.file("local.properties")
        if (localProps.exists()) {
            val props = Properties().apply { load(FileInputStream(localProps)) }
            props.getProperty("TEST_VOXTRAL_KEY")?.let {
                testInstrumentationRunnerArguments["voxtralKey"] = it
            }
            props.getProperty("TEST_OPENAI_KEY")?.let {
                testInstrumentationRunnerArguments["openaiKey"] = it
            }
            props.getProperty("TEST_GROQ_KEY")?.let {
                testInstrumentationRunnerArguments["groqKey"] = it
            }
        }
    }

    signingConfigs {
        create("release") {
            val localProps = rootProject.file("local.properties")
            if (localProps.exists()) {
                val props = Properties().apply { load(FileInputStream(localProps)) }
                storeFile = file(props.getProperty("RELEASE_STORE_FILE"))
                storePassword = props.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = props.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = props.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
    implementation("com.google.firebase:firebase-crashlytics")

    // ExecuTorch for on-device ML inference
    implementation("org.pytorch:executorch-android:1.1.0")
    implementation("com.facebook.soloader:soloader:0.10.5")
    implementation("com.facebook.fbjni:fbjni:0.7.0")

    // Moonshine for on-device streaming transcription
    implementation("ai.moonshine:moonshine-voice:0.0.48")

    // JSON parsing
    implementation("org.json:json:20231013")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")

    // Instrumented / E2E testing
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

// Patch 4KB-aligned ELF LOAD segments to 16KB after native libs are merged.
// Moonshine SDK ships .so files with 4KB p_align — Android 15+ requires 16KB.
// This patches the ELF program headers in-place (safe: actual offsets are already aligned).
androidComponents {
    onVariants { variant ->
        val capitalizedName = variant.name.replaceFirstChar { it.uppercase() }
        project.tasks.matching { it.name == "strip${capitalizedName}DebugSymbols" }.configureEach {
            doFirst {
                val mergedDir = file("build/intermediates/merged_native_libs/${variant.name}/merge${capitalizedName}NativeLibs/out/lib")
                if (mergedDir.exists()) {
                    val script = rootProject.file("scripts/fix_elf_alignment.py")
                    val soFiles = mergedDir.walkTopDown().filter { it.extension == "so" }.toList()
                    if (soFiles.isNotEmpty() && script.exists()) {
                        exec {
                            commandLine("python3", script.absolutePath, *soFiles.map { it.absolutePath }.toTypedArray())
                        }
                    }
                }
            }
        }
    }
}

// Apply Firebase plugins only when google-services.json is present
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}
