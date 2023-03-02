
rootProject.name = "lkmp-database"

include(":testing")

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if(requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}
