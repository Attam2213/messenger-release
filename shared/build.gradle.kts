plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization") version "1.9.24"
    id("app.cash.sqldelight") version "2.0.1"
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    jvm("desktop")
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("io.ktor:ktor-client-core:2.3.11")
                implementation("io.ktor:ktor-client-websockets:2.3.11")
                // implementation("io.ktor:ktor-client-cio:2.3.11") // Removed to avoid conflict with iOS Darwin engine
                implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                // SQLDelight
                api("app.cash.sqldelight:runtime:2.0.1")
                api("app.cash.sqldelight:coroutines-extensions:2.0.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
                implementation("io.ktor:ktor-client-okhttp:2.3.11")
                implementation("app.cash.sqldelight:android-driver:2.0.1")
                implementation("com.journeyapps:zxing-android-embedded:4.3.0")
                implementation("androidx.activity:activity-compose:1.8.2")
            }
        }
        val desktopMain by getting {
             dependencies {
                implementation(compose.desktop.common)
                implementation("io.ktor:ktor-client-cio:2.3.11")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
             }
        }
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.11")
                implementation("app.cash.sqldelight:native-driver:2.0.1")
            }
        }
    }
}

sqldelight {
  databases {
    create("AppDatabase") {
      packageName.set("com.example.messenger.shared.db")
    }
  }
}

android {
    namespace = "com.example.messenger.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}
