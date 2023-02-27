package com.darkyen.database

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import java.util.concurrent.CancellationException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Each coroutine dispatched to [ThreadStableDispatcher] must have this in its context.
 * All dispatches that have the same token will be run on the same thread.
 */
class ThreadStableToken : AbstractCoroutineContextElement(Key) {
    internal var assignedThread: Int = -1
    companion object Key : CoroutineContext.Key<ThreadStableToken>
}

/**
 * Thread stable dispatcher for database use.
 * When a task is assigned to a thread, it will always execute on that thread.
 */
class ThreadStableDispatcher(threadCount: Int) : CoroutineDispatcher() {

    private val lock = Object()
    private var closed = false
    private val unassignedQueue = ArrayDeque<Pair<ThreadStableToken?, Runnable>>()
    private val threads = Array(threadCount.coerceAtLeast(1)) { ExecutorThread(it) }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val token = context[ThreadStableToken.Key]
        if (token == null) {
            Log.e("ThreadStableDispatcher", "Executing block without token! $block")
        }
        synchronized(lock) {
            if (closed) {
                context.cancel(CancellationException("ThreadStableDispatcher has been closed"))
                Dispatchers.Default.dispatch(context, block)
                return
            }

            if (token == null || token.assignedThread == -1) {
                unassignedQueue.addLast(token to block)
            } else {
                val queue = threads[token.assignedThread].queue
                queue.addLast(block)
            }
            lock.notifyAll()
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            lock.notifyAll()
        }
    }

    private inner class ExecutorThread(private val index: Int): Thread("ThreadStableExecutor($index)") {

        val queue = ArrayDeque<Runnable>()

        override fun run() {
            while (true) {
                val runnable = synchronized(lock) {
                    var runnable: Runnable?
                    while (true) {
                        runnable = queue.removeFirstOrNull()
                        if (runnable != null) break
                        val pair = unassignedQueue.removeFirstOrNull()
                        if (pair != null) {
                            pair.first?.assignedThread = index
                            runnable = pair.second
                            break
                        }
                        if (closed) break
                        lock.wait()
                    }
                    runnable
                } ?: break

                try {
                    runnable.run()
                } catch (e: Throwable) {
                    getDefaultUncaughtExceptionHandler()?.uncaughtException(this, e)
                }
            }
        }
    }
}