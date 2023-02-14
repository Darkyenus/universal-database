package com.darkyen.ud

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget
import kotlin.coroutines.resume

/**
 * Return the next event of one of the specified [types] emitted by the target.
 * The type of the event is in [Event.type].
 */
internal suspend fun EventTarget.nextEvent(
    vararg types: String,
): Event = suspendCancellableCoroutine { continuation ->
    val eventListener = object : EventListener {
        fun remove() {
            for (type in types) {
                removeEventListener(type, this)
            }
        }

        override fun handleEvent(event: Event) {
            remove()
            if (event.type !in types) {
                continuation.cancel(AssertionError("Event.type = ${event.type}, expected one of ${types.contentToString()}"))
            }
            continuation.resume(event)
        }
    }

    for (type in types) {
        addEventListener(type, eventListener)
    }
    continuation.invokeOnCancellation {
        eventListener.remove()
    }
}

internal external class DOMException {
    val name: String
    val message: String
}

internal fun wrapException(e: dynamic): Throwable {
    if (e is Throwable) return e.unsafeCast<Throwable>()
    val dom = asDOMException(e)
    if (dom != null) return RuntimeException("DOMException(${dom.name}: ${dom.message})")
    return RuntimeException("JS(${e})")
}

internal fun asDOMException(e: dynamic): DOMException? {
    if (jsTypeOf(e) == "object" && window.asDynamic().DOMException.prototype.isPrototypeOf(e).unsafeCast<Boolean>()) {
        return e.unsafeCast<DOMException>()
    }
    return null
}

/** Returns [e] as [DOMException] if it is one, rethrows [e] otherwise. */
internal fun catchDOMException(e: dynamic): DOMException {
    return asDOMException(e) ?: throw e.unsafeCast<Throwable>()
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