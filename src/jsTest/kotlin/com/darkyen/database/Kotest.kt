package com.darkyen.database

import io.kotest.core.spec.style.FunSpec
import com.juul.indexeddb.external.indexedDB
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual fun FunSpec.databaseTest(name: String, schema: Schema, test: suspend (BackendDatabaseConfig) -> Unit) {
    test(name) {
        val config = BackendDatabaseConfig("TESTDB", schema)
        try {
            test(config)
        } finally {
            try {
                deleteUniversalDatabase(config)
            } finally {
                window.indexedDB!!.deleteDatabase(config.name).result()
            }
        }
    }
}


actual fun FunSpec.databaseTest(name: String, schema: List<Schema>, test: suspend (List<BackendDatabaseConfig>) -> Unit) {
    test(name) {
        val configs = ArrayList<BackendDatabaseConfig>()
        for ((i, s) in schema.withIndex()) {
            configs.add(BackendDatabaseConfig("TESTDB", *schema.take(i + 1).toTypedArray()))
        }
        try {
            test(configs)
        } finally {
            for (config in configs) {
                try {
                    deleteUniversalDatabase(config)
                } finally {
                    window.indexedDB!!.deleteDatabase(config.name).result()
                }
            }
        }
    }
}

actual suspend fun doSomethingSuspending(kind: Int) {
    if (kind == 0) {
        window.fetch("/the-url-does-not-matter-suspend-does").await()
    } else if (kind == 1) {
        suspendCoroutine { cont ->
            window.setTimeout({
                cont.resume(Unit)
            }, 10)
        }
    }
}