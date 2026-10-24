plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release builds take their version from the git tag (.github/workflows/release.yml sets
// VERSION_NAME); everything else builds as 0.1.0.
val appVersionName = System.getenv("VERSION_NAME")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "0.1.0"
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

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
}
