plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds take their version from the git tag (.github/workflows/release.yml sets
// VERSION_NAME); everything else builds as 0.2.0.
val appVersionName = System.getenv("VERSION_NAME")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "0.2.0"
/** 1.2.3 → 10203, so each release's versionCode is higher than the one before. */
val appVersionCode = appVersionName.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    .let { it.getOrElse(0) { 0 } * 10_000 + it.getOrElse(1) { 0 } * 100 + it.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

android {
    namespace = "com.onetake.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.onetake.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Only when a key is provided (release.yml decodes it from repository secrets); without one,
        // assembleRelease produces an unsigned APK.
        System.getenv("ONETAKE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("ONETAKE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ONETAKE_KEY_ALIAS")
                keyPassword = System.getenv("ONETAKE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrinks and optimises the release APK. CI builds the release variant on every push,
            // so a missing keep rule shows up as a failed build rather than in a published APK.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
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
        freeCompilerArgs += listOf("-Xskip-metadata-version-check", "-Xskip-prerelease-check")
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // AppStore is an AndroidViewModel; its JVM tests build it with a stub Application, which
        // throws "Stub!" unless android.jar methods return defaults.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        noCompress += listOf("litertlm", "task", "tflite")
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // ML Kit OCR
    implementation(libs.mlkit.text.recognition)
    // On-device face detection for Tape check's framing checks
    implementation(libs.mlkit.face.detection)

    // Home-screen widget (Next audition)
    implementation(libs.androidx.glance.appwidget)

    // 3-Tier Hybrid AI "Reading the Notice"
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta5")
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
}

configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21",
            "org.jetbrains.kotlin:kotlin-reflect:2.0.21",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.9.0"
        )
    }
}

