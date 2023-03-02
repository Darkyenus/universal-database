import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.stackoverflowusers.file.WindowsShortcut
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

plugins {
    kotlin("multiplatform") version "1.7.22"
    id("com.android.application")
    id("io.kotest.multiplatform") version "5.5.5"
}

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

kotlin {
    android {}
    js(IR) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                outputFileName = "testing.js"
                sourceMaps = true
                devtool = "source-map"
            }
            @Suppress("OPT_IN_USAGE")
            distribution {
                name = "testing"
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
                implementation(project(":"))
                implementation("io.kotest:kotest-framework-datatest:$kotest")
                implementation("io.kotest:kotest-assertions-core:$kotest")
                implementation("io.kotest:kotest-property:$kotest")
            }
        }
        val androidMain by getting
        val jsMain by getting
    }
}

android {
    namespace = "com.darkyen.database"
    compileSdk = 33
    defaultConfig {
        minSdk = 16
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_9
        targetCompatibility = JavaVersion.VERSION_1_9
    }
}

fun collectBrowsersWindows(): List<suspend (File) -> Unit> {
    // Go through Start menu folders, collect familiar links, extract exe paths from them
    return listOf(
        File(File(System.getProperty("user.home")), "/AppData/Roaming/Microsoft/Windows/Start Menu/Programs"),
        File("C:/ProgramData/Microsoft/Windows/Start Menu/Programs")
    ).flatMap { startDir ->
        if (!startDir.isDirectory) {
            emptyList()
        } else {
            startDir.listFilesOrdered { name -> name.extension == "lnk" }
        }
    }.mapNotNull { link ->
        when (val browser = link.nameWithoutExtension.toLowerCase()) {
            "firefox",
            "firefox developer edition",
            "chromium",
            "chrome",
            "microsoft edge" -> {
                try {
                    val shortcut = WindowsShortcut(link)
                    if (!shortcut.isLocal || shortcut.isDirectory || (shortcut.realFilename == null && shortcut.relativePath == null)) {
                        null
                    } else {
                        { htmlFile ->
                            val process = ProcessBuilder().apply {
                                if (shortcut.workingDirectory != null) {
                                    directory(File(shortcut.workingDirectory))
                                }
                                val commands = ArrayList<String>()
                                commands.add(shortcut.realFilename ?: shortcut.relativePath)
                                when (browser) {
                                    "chromium", "chrome", "microsoft edge" -> commands.add("--guest")
                                }
                                commands.add(htmlFile.absolutePath)
                                command(commands)
                            }.start()

                            suspendCancellableCoroutine<Unit> { cont ->
                                cont.invokeOnCancellation {
                                    process.destroy()
                                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                                        process.destroyForcibly()
                                    }
                                }
                                process.onExit().thenRunAsync {
                                    cont.resume(Unit)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    null
                }
            }
            else -> null
        }
    }
}

fun collectBrowsers(): List<suspend (File) -> Unit> {
    if (System.getProperty("os.name").contains("Windows")) {
        return collectBrowsersWindows()
    }
    throw NotImplementedError("No collectBrowsers for OS ${System.getProperty("os.name")}")
}

tasks.register("runTests") {
    outputs.upToDateWhen { false }// Do not cache tests

    val webpack = tasks["jsBrowserDevelopmentWebpack"] as org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
    dependsOn(webpack)

    doLast {
        val jsFile = webpack.outputs.files.files.first { it.name == "testing.js" }
        val htmlFile = jsFile.resolveSibling("index.html")
        htmlFile.writeText("<!DOCTYPE html><html><head><title>lkmp-database testing</title><script defer src=\"testing.js\"></script></head><body>Not loaded</body></html>")
        runBlocking {
            collectBrowsers().map { runBrowser -> async { runBrowser(htmlFile) } }.awaitAll()
        }
    }
}

