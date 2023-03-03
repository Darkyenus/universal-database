package com.darkyen.database

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds all tests
 */
abstract class TestContainer {

    constructor(body: TestContainer.() -> Unit) {
        this.name = this::class.simpleName ?: "_"
        body()
    }
    private constructor(name: String) {
        this.name = name
    }

    private var name: String = ""
    private var parent: TestContainer? = null
    private var tests: ArrayList<Test>? = ArrayList()

    fun include(testContainer: TestContainer) {
        if (testContainer.parent != null) throw IllegalStateException("$testContainer already has a parent")
        testContainer.parent = this
        testContainer.tests?.forEach { test ->
            addTest(testContainer.name+"."+test.name, test.function)
        }
        testContainer.tests = null
    }

    fun container(name: String, body: TestContainer.() -> Unit) {
        val container = object : TestContainer(name) {}
        include(container)
        container.body()
    }

    private fun addTest(name: String, body: suspend () -> Unit) {
        val parent = parent
        if (parent != null) {
            parent.addTest(this.name+"."+name, body)
        } else {
            tests!!.add(Test(name, body))
        }
    }

    fun test(name: String, body: suspend () -> Unit) {
        val illegalChar = name.firstOrNull { !it.isLetterOrDigit() && it !in " _:!" }
        if (illegalChar != null) throw IllegalArgumentException("Test names must consist of letters, digits, space and underscore: '$name' ($illegalChar)")
        addTest(name, body)
    }

    suspend fun runTests(): Flow<List<TestResultEntry>> {
        val tests = tests?.sortedBy {
            it.name
        } ?: throw IllegalStateException("This is not the root container!")
        val commonPrefixLength = tests.map { it.name }.reduce { a, b -> a.commonPrefixWith(b) }.let { commonPrefix ->
            val sep = commonPrefix.lastIndexOf('.')
            if (sep <= 0) {
                0
            } else {
                sep + 1
            }
        }
        // Find focus test
        return flow {
            val result = ArrayList<TestResultEntry>()
            val focusTests = tests.mapNotNullTo(HashSet()) { if (it.name.startsWith("f:") || it.name.contains(".f:")) it.name else null }
            for (test in tests) {
                val skip = test.name.startsWith("!") || test.name.contains(".!") || (focusTests.isNotEmpty() && test.name !in focusTests)
                val entry = TestResultEntry(test.name.substring(commonPrefixLength), test.function)
                if (skip) {
                    // Skipped
                    entry.status = TestResultEntry.Status.Skipped
                }
                result.add(entry)
            }
            for (entry in result) {
                if (entry.status != TestResultEntry.Status.Waiting) {
                    continue
                }
                println("Test ${entry.name}: ")
                entry.status = TestResultEntry.Status.Running
                emit(result)

                try {
                    val timeout = withTimeoutOrNull(20_000) {
                        entry.function()
                    }
                    if (timeout == null) {
                        entry.status = TestResultEntry.Status.TimedOut
                        println("     Timed out")
                    } else {
                        entry.status = TestResultEntry.Status.Success
                        println("     Success")
                    }
                } catch (e: Throwable) {
                    entry.failException = e
                    println("     Failure: ${e.stackTraceToString()}")
                    e.printStackTrace()
                    entry.status = TestResultEntry.Status.Failed
                }
            }
            emit(result)
        }
    }

    suspend fun runAndRenderTests(): Flow<String> {
        return try {
            runTests().map { it.renderHTMLTable() }
        } catch (e: Throwable) {
            flowOf(e.stackTraceToString())
        }
    }
}

class TestResultEntry(val name: String, val function: suspend () -> Unit) {
    var status: Status = Status.Waiting
    var failException: Throwable? = null

    enum class Status {
        Skipped,
        Running,
        Waiting,
        Success,
        Failed,
        TimedOut
    }
}

typealias TestResult = List<TestResultEntry>

fun TestResult.renderHTMLTable(): String {
    return buildString {
        append("<table>")
        append("<tr><th>Name</th><th>Result</th></tr>")
        for (entry in this@renderHTMLTable) {
            append("<tr><td>").append(entry.name).append("</td><td style=\"")
            when (entry.status) {
                TestResultEntry.Status.Skipped -> append("background: #555555\">⏩")
                TestResultEntry.Status.Running -> append("background: #FFFF55\">\uD83C\uDFC3")
                TestResultEntry.Status.Waiting -> append("\">⏳")
                TestResultEntry.Status.Success -> append("background: #55FF55\">✅")
                TestResultEntry.Status.Failed -> append("background: #FF5555\">\uD83E\uDD80 ").append(entry.failException?.stackTraceToString())
                TestResultEntry.Status.TimedOut -> append("background: #FF5555\">\uD83D\uDD70️")
            }
            append("</td>")
        }
        append("</table>")
    }
}

private class Test(val name: String, val function: suspend () -> Unit)

inline fun TestContainer.databaseTest(
    name: String,
    schema: Schema,
    crossinline test: suspend (BackendDatabaseConfig) -> Unit) {
    databaseTest(name, listOf(schema)) { (config) ->
        test(config)
    }
}

expect fun TestContainer.databaseTest(name: String, schema: List<Schema>, test: suspend (List<BackendDatabaseConfig>) -> Unit)

suspend inline fun <T> withDatabase(config: BackendDatabaseConfig, block: (Database) -> T):T {
    val result = openUniversalDatabase(config)
    result.shouldBeInstanceOf<OpenDBResult.Success>()
    val db = result.db
    try {
        return block(db)
    } finally {
        db.close()
    }
}

class ClueThrowable : Throwable(null, null) {
    private val clues = ArrayList<String>()
    fun addClue(clue: String) {
        clues.add(clue)
    }

    override val message: String
        get() {
            return "Clue: "+clues.joinToString(" - ")
        }
}

fun Throwable.findClue(): ClueThrowable? {
    var t = this
    while (true) {
        for (se in suppressedExceptions) {
            if (se is ClueThrowable) {
                return se
            } else {
                se.findClue()?.let { return it }
            }
        }
        t = t.cause ?: break
    }
    return null
}

fun Throwable.addClue(clue: Any) {
    val clueThrowable = findClue() ?: run {
        val c = ClueThrowable()
        addSuppressed(c)
        c
    }
    val str = clue.toString()
    println("CLUE: $str")
    clueThrowable.addClue(str)
}

inline fun <R> withClue(clue: Any, block: () -> R):R {
    try {
        return block()
    } catch (e: Throwable) {
        e.addClue(clue.toString())
        throw e
    }
}

inline fun <R> withClue(clue: () -> Any, block: () -> R):R {
    try {
        return block()
    } catch (e: Throwable) {
        e.addClue(clue().toString())
        throw e
    }
}

expect suspend fun doSomethingSuspending(kind: Int)

/** Convert [Double] to [Float]. Same as [Double.toFloat] but actually works in JS backend.
 * See https://youtrack.jetbrains.com/issue/KT-24975/Enforce-range-of-Float-type-in-JS
 * and https://youtrack.jetbrains.com/issue/KT-35422/Fix-IntUIntDouble.toFloat-in-K-JS */
expect fun doubleToFloat(v: Double): Float

expect object RootTestContainer : TestContainer
