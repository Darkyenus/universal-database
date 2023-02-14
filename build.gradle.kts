plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("io.kotest.multiplatform") version "5.5.5"
}

group = "com.darkyen"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

kotlin {
    android {}
    js(IR) {
        browser {
            webpackTask {
            }
            testTask {
                useKarma {
                    useFirefoxDeveloperHeadless()
                    useChromiumHeadless()
                }
            }
        }
    }

    val kotest = "5.5.5"
    
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }

        val commonMain by getting {
            dependencies {
                // Coroutines
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

                // CBOR
                api("com.darkyen.ultralight-cbor:ultralight-cbor:2b0aa48fcc")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("io.kotest:kotest-framework-engine:$kotest")
                implementation("io.kotest:kotest-framework-datatest:$kotest")//???
                implementation("io.kotest:kotest-assertions-core:$kotest")
                implementation("io.kotest:kotest-property:$kotest")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
            }
        }
        val androidInstrumentedTest by getting
        val jsMain by getting {
            dependencies {
                // IndexedDB external declarations
                implementation("com.juul.indexeddb:external:0.6.0")
            }
        }
        val jsTest by getting {
            dependencies {
                // IndexedDB wrapper
                implementation("com.juul.indexeddb:core:0.6.0")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        apiVersion = "1.8"
    }
}

android {
    namespace = "com.darkyen.ud"
    compileSdk = 33
    defaultConfig {
        minSdk = 16
    }
}
