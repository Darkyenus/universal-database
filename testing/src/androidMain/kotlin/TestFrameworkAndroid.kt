package com.darkyen.database

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("StaticFieldLeak")
lateinit var testAndroidContext: Context

actual fun TestContainer.databaseTest(name: String, schema: List<Schema>, test: suspend (List<BackendDatabaseConfig>) -> Unit) {
    test(name) {
        val configs = ArrayList<BackendDatabaseConfig>()
        for ((i, s) in schema.withIndex()) {
            configs.add(BackendDatabaseConfig("TESTDB", *schema.take(i + 1).toTypedArray(), context = testAndroidContext))
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
})

class TestRunnerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(webView)
        webView.loadData("<html><body>Running tests</body></html>", "text/html", "UTF-8")

        GlobalScope.launch {
            RootTestContainer.runAndRenderTests().collect { html ->
                withContext(Dispatchers.Main) {
                    webView.loadData("<html><body>$html</body></html>", "text/html", "UTF-8")
                }
            }
        }
    }
}