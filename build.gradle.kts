plugins {
    kotlin("multiplatform") version "1.7.22"
    id("com.android.library") version "7.3.0"
    id("io.kotest.multiplatform") version "5.5.5"
    `maven-publish`
}

group = "com.darkyen"
version = "1.0"

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

kotlin {
    android {}
    js(IR) {
        browser {
            testTask {
                useKarma {
                    //useCoverage(html = true, lcov = false, teamcity = false) does not work, only computes coverage for test launcher shim
                    //useSourceMapSupport() does nothing
                    useConfigDirectory("karma-config")

                    if (true) {
                        useChromium()
                        useFirefoxDeveloper()
                    } else {
                        useChromiumHeadless()
                        useFirefoxDeveloperHeadless()
                    }
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
                api("com.darkyen.lkmp-cbor:lkmp-cbor:1.0")
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
                implementation("com.github.requery:sqlite-android:3.39.2")
                implementation("androidx.sqlite:sqlite:2.2.0")// Do not update until Requery does
            }
        }
        val androidTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:$kotest")
            }
        }
        val jsMain by getting {
            dependencies {
                // IndexedDB external declarations
                implementation("com.juul.indexeddb:external:0.6.0")
            }
        }
        val jsTest by getting
    }
}

android {
    namespace = "com.darkyen.database"
    compileSdk = 33
    defaultConfig {
        minSdk = 16
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}
