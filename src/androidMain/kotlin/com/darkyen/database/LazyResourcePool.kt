package com.darkyen.database

import android.util.Log
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger

internal class LazyResourcePool <Res:AutoCloseable> (
    /** Stable resource that will not be automatically released */
    stable: Res,
    /** Do not create more lazy resources than this */
    lazyBudget: Int,
    /** Called to create a new resource, lazily, when needed */
    private val createLazyResource: () -> Res) {

    @Volatile
    private var closed = false

    /** permits = how many elements are in pool */
    // 1 permit for stable + lazyBudget prepared permits for potential lazy permits and + 1 prepared permit for close
    private val poolAvailable = Semaphore(1 + lazyBudget + 1, 1 + lazyBudget)
    /** Contains unused resources, free for taking (if you have the permit) */
    private val pool = ArrayList<Res>(1 + lazyBudget)
    init {
        pool.add(stable)
    }

    /** How many lazy resources can still be created */
    private val lazyBudget = AtomicInteger(lazyBudget)

    private val lazyCreationFailures = AtomicInteger(0)

    private fun createNewResourceIfBudgetAllows(): Res? {
        if (lazyCreationFailures.get() > 10) return null// Don't try anymore

        while (true) {
            val budget = lazyBudget.get()
            if (budget <= 0) return null // There is no budget for further allocations
            if (lazyBudget.compareAndSet(budget, budget - 1)) {
                break
            }
            // Try again...
        }

        // We are allowed to create a new resource!
        return try {
            val result = createLazyResource()
            lazyCreationFailures.set(0)// It works (again)!
            result
        } catch (e: Throwable) {
            Log.e("LazyResourcePool", "Failed to create new resource", e)
            lazyBudget.incrementAndGet()// Return allowance to the pool
            lazyCreationFailures.incrementAndGet()// Remember this to make sure that we don't degrade performance by trying too much
            null
        }
    }

    suspend fun obtainResource(): Res {
        // Fast path
        if (poolAvailable.tryAcquire()) {
            synchronized(pool) {
                if (closed) {
                    poolAvailable.release()
                    throw IllegalStateException("closed")
                }
                return pool.removeLast()
            }
        }

        // Create new
        val newRes = createNewResourceIfBudgetAllows()
        if (newRes != null) {
            return newRes
        }

        // Wait
        poolAvailable.acquire()
        return synchronized(pool) {
            if (closed) {
                poolAvailable.release()
                throw IllegalStateException("closed")
            }
            pool.removeLast()
        }
    }

    fun releaseResource(res: Res) {
        try {
            synchronized(pool) {
                if (closed) {
                    res.close()
                } else {
                    pool.add(res)
                }
            }
        } finally {
            poolAvailable.release()
        }
    }

    suspend inline fun <R> withResource(block: (Res) -> R): R {
        val resource = obtainResource()
        try {
            return block(resource)
        } finally {
            releaseResource(resource)
        }
    }

    fun close() {
        if (closed) return
        var err: Throwable? = null
        synchronized(pool) {
            closed = true
            while (pool.isNotEmpty()) {
                val res = pool.removeLast()
                try {
                    res.close()
                } catch (e: Throwable) {
                    err.let {
                        if (it == null) err = e
                        else it.addSuppressed(e)
                    }
                }
            }
            // Unblock waiting threads
            poolAvailable.release()
        }
        err?.let { throw it }
    }
}