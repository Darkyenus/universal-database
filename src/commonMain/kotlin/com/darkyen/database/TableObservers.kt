package com.darkyen.database

class TableObservers<V> {

    private val lock = SynchronizedObject()
    private var count = 0
    private var sets = arrayOfNulls<TableSet>(8)
    private var observers = arrayOfNulls<Any>(8)

    private var tmpObservers: Array<Any?>? = null

    fun addObserver(tables: TableSet, observer: V) {
        synchronized(lock) {
            val index = count++
            if (index == sets.size) {
                sets = sets.copyOf(index * 2)
                observers = observers.copyOf(index * 2)
            }
            sets[index] = tables
            observers[index] = observer
        }
    }

    fun removeObserver(observer: V) {
        synchronized(lock) {
            val count = count
            val sets = sets
            val observers = observers
            var index = 0
            while (true) {
                if (index >= count) {
                    // Not present at all
                    return
                }
                if (observers[index] === observer) {
                    break
                }
                index += 1
            }
            val lastIndex = count - 1
            if (index != lastIndex) {
                sets[index] = sets[lastIndex]
                observers[index] = observers[lastIndex]
            }
            sets[lastIndex] = null
            observers[lastIndex] = null
            this.count = lastIndex
        }
    }

    @PublishedApi
    internal fun internalObtainObservers(tables: TableSet): Array<Any?> {
        return synchronized (lock) {
            var result = tmpObservers
            tmpObservers = null
            if (result == null || result.size < count) {
                result = arrayOfNulls(count)
            }

            val count = count
            val observers = observers
            val sets = sets
            var out = 0
            for (i in 0 until count) {
                if (sets[i]!! intersects tables) {
                    result[out++] = observers[i]
                }
            }
            result
        }
    }

    @PublishedApi
    internal fun internalReturnObservers(array: Array<Any?>) {
        synchronized (lock) {
            val currentObservers = tmpObservers
            if (currentObservers == null || currentObservers.size < array.size) {
                tmpObservers = array
            }
        }
    }

    fun forEachObserver(tables: TableSet, handleObserver: (V) -> Unit) {
        val observers = internalObtainObservers(tables)

        var error: Throwable? = null
        var i = 0
        while (i < observers.size) {
            val observer = observers[i] ?: break
            observers[i] = null// Clear for future reuse
            i++

            try {
                @Suppress("UNCHECKED_CAST")
                handleObserver(observer as V)
            } catch (e: Throwable) {
                if (error == null) {
                    error = e
                } else {
                    error.addSuppressed(e)
                }
            }
        }

        internalReturnObservers(observers)
        if (error != null) {
            throw error
        }
    }
}