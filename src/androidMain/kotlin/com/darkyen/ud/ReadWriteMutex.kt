package com.darkyen.ud

import kotlinx.coroutines.sync.Semaphore

/**
 * See
 * https://github.com/Kotlin/kotlinx.coroutines/issues/94
 * https://greenteapress.com/semaphores/LittleBookOfSemaphores.pdf
 * Replace with https://github.com/Kotlin/kotlinx.coroutines/pull/2045 when it merges
 */
class ReadWriteMutex {
    // The counter readers keeps track of how many readers are in the room.
    @PublishedApi internal var readers = 0
    // protects the shared counter readers
    @PublishedApi internal val mutex = Semaphore(1)
    // is 1 if there are no threads (readers or writers) in the critical section, and 0 otherwise
    @PublishedApi internal val roomEmpty = Semaphore(1)

    suspend inline fun <R> write(block: () -> R): R {
        roomEmpty.acquire()
        try {
            return block()
        } finally {
            roomEmpty.release()
        }
    }

    suspend inline fun <R> read(block: () -> R): R {
        mutex.acquire()
        if (++readers == 1) {
            roomEmpty.acquire()
        }
        mutex.release()

        try {
            return block()
        } finally {
            mutex.acquire()
            if (--readers == 0) {
                roomEmpty.release()
            }
            mutex.release()
        }
    }
}