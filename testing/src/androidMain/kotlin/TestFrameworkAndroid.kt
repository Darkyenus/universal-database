package com.darkyen.database

import android.annotation.SuppressLint
import android.content.Context
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("StaticFieldLeak")
lateinit var testAndroidContext: Context

actual fun TestContainer.databaseTest(name: String, schema: List<Schema>, test: suspend (List<BackendDatabaseConfig>) -> Unit) {
    test(name) {
        val configs = ArrayList<BackendDatabaseConfig>()
        for ((i, s) in schema.withIndex()) {
            configs.add(BackendDatabaseConfig(
                "TESTDB",
                *schema.take(i + 1).toTypedArray(),
                context = testAndroidContext,
                parallelism = 5 // Some tests need this
                ))
        }
        try {
            test(configs)
        } finally {
            for (config in configs) {
                deleteUniversalDatabase(config)
            }
        }
    }
}

actual suspend fun doSomethingSuspending(kind: Int) {
    suspendCoroutine { cont ->
        thread(true) {
            Thread.sleep(100)
            cont.resume(Unit)
        }
    }
}

actual fun doubleToFloat(v: Double): Float {
    return v.toFloat()
}

actual object RootTestContainer : TestContainer({
    include(CommonTests)
    include(AndroidDatabaseTest)
})
