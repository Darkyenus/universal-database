@file:Suppress("NOTHING_TO_INLINE")

package com.darkyen.ud

import com.darkyen.ucbor.CborSerializer

/** JS has no threads, so this is safe */
internal val serializationHelper = SerializationHelper()

internal actual inline fun <T: Any> serialize(serializer: CborSerializer<T>, value: T): ByteArray = serializationHelper.serialize(serializer, value)
internal actual inline fun <T: Any> serialize(serializer: KeySerializer<T>, value: T): ByteArray = serializationHelper.serialize(serializer, value)
internal actual inline fun <T: Any> deserialize(serializer: CborSerializer<T>, value: ByteArray): T = serializationHelper.deserialize(serializer, value)
internal actual inline fun <T: Any> deserialize(serializer: KeySerializer<T>, value: ByteArray): T = serializationHelper.deserialize(serializer, value)
