package com.darkyen.database

expect open class SynchronizedObject constructor()

expect inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R