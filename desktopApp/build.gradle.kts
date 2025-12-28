plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
    jvm() {
        withJava()
    }
    
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.messenger.desktop.MainKt"
        javaHome = "C:/Users/ILJES/scoop/persist/gradle/.gradle/jdks/eclipse_adoptium-17-amd64-windows/jdk-17.0.17+10"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "Messenger"
            packageVersion = "1.0.0"
            includeAllModules = true
            
            windows {
                console = false
            }
        }
    }
}

