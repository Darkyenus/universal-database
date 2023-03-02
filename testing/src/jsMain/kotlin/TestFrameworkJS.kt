package com.darkyen.database

import com.juul.indexeddb.external.IDBRequest
import com.juul.indexeddb.external.indexedDB
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

actual fun TestContainer.databaseTest(name: String, schema: List<Schema>, test: suspend (List<BackendDatabaseConfig>) -> Unit) {
    test(name) {
        val configs = ArrayList<BackendDatabaseConfig>()
        for ((i, s) in schema.withIndex()) {
            configs.add(BackendDatabaseConfig("TESTDB", *schema.take(i + 1).toTypedArray()))
        }
        try {
            deleteUniversalDatabase(configs[0])
            test(configs)
        } finally {
            delay(1)// Break any IDB transactions
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

private suspend fun <T> IDBRequest<T>.result():T {
    return withContext(Dispatchers.Unconfined) {
        suspendCancellableCoroutine { cont ->
            onsuccess = {
                cont.resume(this@result.result)
            }
            onerror = {
                it.preventDefault()
                it.stopPropagation()
                cont.resumeWithException(this@result.error as Throwable)
            }
        }
    }
}

actual suspend fun doSomethingSuspending(kind: Int) {
    println("doSomethingSuspending($kind)")
    var exception: Throwable? = null
    try {
        if (kind == 0) {
            val fetch = Promise { resolve, reject ->
                try {
                    window.requestAnimationFrame {
                        resolve.invoke(Unit)
                    }
                } catch (e: Throwable) {
                    reject.invoke(e)
                }
            }
            suspendCoroutine { cont ->
                fetch.then({
                    cont.resume(Unit)
                }, {
                    cont.resumeWithException(it)
                })
            }
        } else if (kind == 1) {
            suspendCoroutine { cont ->
                window.setTimeout({
                    cont.resume(Unit)
                }, 10)
            }
        }
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        println("doSomethingSuspending($kind) - done ($exception)")
    }
}

actual fun doubleToFloat(v: Double): Float {
    // https://blog.mozilla.org/javascript/2013/11/07/efficient-float32-arithmetic-in-javascript/
    val fround = window.asDynamic().Math.fround
    if (fround !== undefined) {
        return fround(v).unsafeCast<Float>()
    }
    return v.toFloat()// Does nothing, but it is an acceptable fallback
}

actual object RootTestContainer : TestContainer({
    include(CommonTests)
})

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    window.document.body!!.textContent = "Running tests..."
    GlobalScope.launch {
        RootTestContainer.runAndRenderTests().collect { html ->
            window.document.body!!.innerHTML = html
        }
    }
}