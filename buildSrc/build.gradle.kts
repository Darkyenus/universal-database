plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle:7.3.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}