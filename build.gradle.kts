plugins {
    kotlin("multiplatform") version "1.7.22"
    id("com.android.library") version "7.3.0"
    id("io.kotest.multiplatform") version "5.5.5"
    `maven-publish`
}

group = "com.darkyen"
version = "1.4"

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

kotlin {
    android {
        publishAllLibraryVariants()
    }
    js(IR) {
        browser {
        }
    }
    
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }

        val commonMain by getting {
            dependencies {
                // Coroutines
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

                // CBOR
                api("com.darkyen.lkmp-cbor:lkmp-cbor:1.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
                implementation("com.github.Darkyenus:sqlitelite:3.41.0.0")
            }
        }
        val jsMain by getting {
            dependencies {
                // IndexedDB external declarations
                implementation("com.juul.indexeddb:external:0.6.0")
            }
        }
    }
}

android {
    namespace = "com.darkyen.database"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}
