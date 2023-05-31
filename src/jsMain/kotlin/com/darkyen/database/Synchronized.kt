package com.darkyen.database

actual typealias SynchronizedObject = Any

actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R {
    return block()
}