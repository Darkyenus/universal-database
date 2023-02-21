package com.darkyen.ud

import kotlinx.coroutines.CancellationException
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

internal fun <T> Result<T>.addSuppressed(t: Throwable): Result<T> {
    return this.fold({ Result.failure(t) }, { e ->
        e.addSuppressed(t)
        Result.failure(e)
    })
}

class KDOMException(val name: String, val domMessage: String) : RuntimeException("$name: $domMessage")

internal inline fun <R> reinterpretExceptions(block: () -> R): R {
    try {
        return block()
    } catch (e: dynamic) {
        throw reinterpretException(e)
    }
}

internal inline fun <R> reinterpretExceptions(tracer: Throwable?, block: () -> R): R {
    try {
        return block()
    } catch (e: dynamic) {
        val t = reinterpretException(e)
        if (tracer != null) {
            t.addSuppressed(tracer)
        }
        throw t
    }
}

internal fun reinterpretException(e: dynamic): Throwable {
    val t: Throwable = if (js("e instanceof DOMException").unsafeCast<Boolean>()) {
        if (e.name == "ConstraintError") ConstraintException(e.message.unsafeCast<String>())
        else if (e.name == "QuotaExceededError") QuotaException(e.message.unsafeCast<String>())
        else KDOMException(e.name.unsafeCast<String>(), e.message.unsafeCast<String>())
    } else if (js("e instanceof Error").unsafeCast<Boolean>()) {
        // https://kotlinlang.org/docs/js-to-kotlin-interop.html#primitive-arrays
        if (e is CancellationException) {
            throw e.unsafeCast<CancellationException>()
        }
        e.unsafeCast<Throwable>()
    } else {
        RuntimeException("Raw exception (${e}})")
    }
    if (DEBUG) {
        try {
            console.log("UniversalDatabase error", e, t)
            console.asDynamic().trace()
        } catch (ignored: dynamic) {}
    }
    return t
}

/** Turns [v], which is asserted to be an [ArrayBuffer] into ordinary [ByteArray]. */
internal fun arrayBufferToByteArray(v: dynamic): ByteArray {
    if (js("v instanceof Int8Array").unsafeCast<Boolean>()) {
        return v.unsafeCast<ByteArray>()
    }
    if (js("v instanceof ArrayBuffer").unsafeCast<Boolean>()) {
        return Int8Array(v.unsafeCast<ArrayBuffer>()).unsafeCast<ByteArray>()
    }
    throw RuntimeException("Expected ArrayBuffer or Int8Array, got '$v' (${v.constructor})")
}