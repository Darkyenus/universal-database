package com.darkyen.ud

import com.darkyen.ucbor.ByteData
import com.darkyen.ucbor.CborRead
import com.darkyen.ucbor.CborSerializer
import com.darkyen.ucbor.CborWrite

/** Thread-unsafe class that holds pre-allocated object which help with de/serialization of objects. */
internal class SerializationHelper {

    /** Shared buffer used for de/serialization of all kinds of values. */
    private val readByteData = ByteData()// Does not carry its own buffer
    private val cborRead = CborRead(readByteData)
    private val writeByteData = ByteData()// Does carry its own buffer
    private val cborWrite = CborWrite(writeByteData)

    fun <T: Any> serialize(serializer: CborSerializer<T>, value: T): ByteArray {
        val byteData = writeByteData
        byteData.resetForWriting(true)
        val cborWrite = cborWrite
        cborWrite.reset()
        cborWrite.value(value, serializer)
        return byteData.toByteArray()
    }
    fun <T: Any> serialize(serializer: KeySerializer<T>, value: T): ByteArray {
        val byteData = writeByteData
        byteData.resetForWriting(true)
        serializer.serialize(byteData, value)
        return byteData.toByteArray()
    }
    fun <T: Any> deserialize(serializer: CborSerializer<T>, value: ByteArray): T {
        val byteData = readByteData
        byteData.resetForReading(value)
        val cborRead = cborRead
        cborRead.reset()
        return cborRead.value(serializer)
    }
    fun <T: Any> deserialize(serializer: KeySerializer<T>, value: ByteArray): T {
        val byteData = readByteData
        byteData.resetForReading(value)
        return serializer.deserialize(byteData)
    }
}

internal expect inline fun <T: Any> serialize(serializer: CborSerializer<T>, value: T): ByteArray
internal expect inline fun <T: Any> serialize(serializer: KeySerializer<T>, value: T): ByteArray
internal expect inline fun <T: Any> deserialize(serializer: CborSerializer<T>, value: ByteArray): T
internal expect inline fun <T: Any> deserialize(serializer: KeySerializer<T>, value: ByteArray): T
