plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
}

android {
    compileSdk = 34
    // buildToolsVersion = "34.0.0"
    namespace = "com.example.messenger"

    defaultConfig {
        applicationId = "com.example.messenger"
        minSdk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.3"
        multiDexEnabled = true
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":shared"))
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.material:material:1.6.1")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.1")
    
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JSON  
    implementation("org.json:json:20231013")
    
    // Cryptography
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // QR Code Generation
    implementation("com.google.zxing:core:3.5.3")

    // CameraX & ML Kit (Scanning)
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.1")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Coil (Image Loading)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Accompanist (Permissions)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // SQLCipher (Database Encryption)
    implementation(files("libs/android-database-sqlcipher-4.5.3.aar"))
    implementation("androidx.sqlite:sqlite:2.3.1")
    implementation("androidx.sqlite:sqlite-framework:2.3.1")


    // Coil (Image Loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Testing
    testImplementation("junit:junit:4.13.2")

    // VK SDK
    implementation("com.vk:android-sdk-core:4.1.0")
    implementation("com.vk:android-sdk-api:4.1.0") // Optional, but useful for API calls

    // WebRTC
    implementation("com.dafruits:webrtc:113.0.0")
}
