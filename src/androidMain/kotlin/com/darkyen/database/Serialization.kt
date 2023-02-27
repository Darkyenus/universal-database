@file:Suppress("NOTHING_TO_INLINE")

package com.darkyen.database

import com.darkyen.ucbor.CborSerializer
import java.util.concurrent.atomic.AtomicReference

private val serializationHelperRef = AtomicReference(SerializationHelper())
private fun borrowSerializationHelper(): SerializationHelper {
    val helper = serializationHelperRef.getAndSet(null)
    return helper ?: SerializationHelper()
}
private fun returnSerializationHelper(helper: SerializationHelper) {
    // I guess it does not matter whether we return it or not when it is already not null
    serializationHelperRef.set(helper)
    //serializationHelperRef.compareAndSet(null, helper)
}

internal inline fun <R> withSerializationHelper(block: SerializationHelper.() -> R): R {
    val helper = borrowSerializationHelper()
    try {
        return block(helper)
    } finally {
        returnSerializationHelper(helper)
    }
}

internal actual inline fun <T: Any> serialize(serializer: CborSerializer<T>, value: T): ByteArray = withSerializationHelper { serialize(serializer, value) }
internal actual inline fun <T: Any> serialize(serializer: KeySerializer<T>, value: T): ByteArray = withSerializationHelper { serialize(serializer, value) }
internal actual inline fun <T: Any> deserialize(serializer: CborSerializer<T>, value: ByteArray): T = withSerializationHelper { deserialize(serializer, value) }
internal actual inline fun <T: Any> deserialize(serializer: KeySerializer<T>, value: ByteArray): T = withSerializationHelper { deserialize(serializer, value) }
