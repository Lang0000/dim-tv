plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ua.home.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "ua.home.launcher"
        minSdk = 30
        // 28 on purpose: WifiManager.setWifiEnabled is a no-op for apps targeting 29+ (quick panel Wi-Fi switch).
        targetSdk = 28
        versionCode = 1
        versionName = "0.1"
        // The TV is 32-bit ARM only; other ABIs are dead weight in the APK.
        ndk { abiFilters += "armeabi-v7a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Personal sideload build: sign with the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Sideloaded personal build; the Play target-SDK rule does not apply.
    lint { disable += "ExpiredTargetSdkVersion" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
