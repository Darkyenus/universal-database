package com.darkyen.database


import io.kotest.core.spec.style.FunSpec
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual fun FunSpec.databaseTest(name: String, schema: Schema, test: suspend (BackendDatabaseConfig) -> Unit) {}

actual fun FunSpec.databaseTest(name: String, schema: List<Schema>, test: suspend (List<BackendDatabaseConfig>) -> Unit) {}

actual suspend fun doSomethingSuspending(kind: Int) {
    suspendCoroutine { cont ->
        thread(true) {
            Thread.sleep(100)
            cont.resume(Unit)
        }
    }
}
