package com.darkyen.database.testing

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import com.darkyen.database.RootTestContainer
import com.darkyen.database.TestResultEntry
import com.darkyen.database.testAndroidContext
import kotlinx.coroutines.*

class TestRunnerActivity : Activity() {

    init {
        testAndroidContext = this
    }

    private lateinit var table: TableLayout

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val table = TableLayout(this)
        table.setColumnShrinkable(0, true)
        table.setColumnShrinkable(1, true)
        val scroll = ScrollView(this)
        scroll.addView(table)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = true
        scroll.isHorizontalScrollBarEnabled = true

        this.table = table
        table.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        table.addView(TableRow(this).apply {
            addView(TextView(context).apply { text = "Test Name" })
            addView(TextView(context).apply { text = "Test Result" })
        })

        setContentView(scroll)
    }

    @SuppressLint("SetTextI18n")
    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()
        GlobalScope.launch(Dispatchers.Main) {
            delay(1000)
            RootTestContainer.runTests().collect { result ->
                for ((i, entry) in result.withIndex()) {
                    val row = table.getChildAt(i+1) as TableRow? ?: run {
                        val row = TableRow(this@TestRunnerActivity).apply {
                            addView(TextView(context).apply { text = entry.name })
                            addView(TextView(context).apply {
                                isSingleLine = false
                            })
                        }
                        table.addView(row)
                        row
                    }

                    val status = row.getChildAt(1) as TextView
                    when (entry.status) {
                        TestResultEntry.Status.Skipped -> {
                            status.setBackgroundColor(0xFF555555.toInt())
                            status.text = "⏩"
                        }
                        TestResultEntry.Status.Running -> {
                            status.setBackgroundColor(0xFFFFFF55.toInt())
                            status.text = "\uD83C\uDFC3"
                        }
                        TestResultEntry.Status.Waiting -> {
                            status.background = null
                            status.text = "⏳"
                        }
                        TestResultEntry.Status.Success -> {
                            status.setBackgroundColor(0xFF55FF55.toInt())
                            status.text = "✅"
                        }
                        TestResultEntry.Status.Failed -> {
                            status.setBackgroundColor(0xFFFF5555.toInt())
                            status.text = "\uD83E\uDD80 "+entry.failException?.stackTraceToString()
                        }
                        TestResultEntry.Status.TimedOut -> {
                            status.setBackgroundColor(0xFFFF5555.toInt())
                            status.text = "\uD83D\uDD70"
                        }
                    }
                }
                delay(100)
            }
        }
    }
}