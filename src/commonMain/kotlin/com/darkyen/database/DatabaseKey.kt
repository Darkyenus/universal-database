package com.darkyen.database

import com.darkyen.ucbor.ByteRead
import com.darkyen.ucbor.ByteWrite
import com.darkyen.ucbor.doubleToFloat

/**
 * CBOR serialization provided by [com.darkyen.ucbor.CborSerializer] can turn, in principle,
 * any object into a binary string. Binary strings can be compared, however the ordering of such
 * serialized objects is different from the ordering of the original objects.
 * Serialization that is stable in regard to ordering must be implemented more carefully
 * and is hard to do generically.
 * Instances of this class provide such serialization scheme.
 */
interface KeySerializer<K:Any> {
    /** Encode [value] into [w], such that the ordering of [value]
     * matches the ordering of the binary strings produced. */
    fun serialize(w: ByteWrite, value: K)

    /** Decode the whole content of [r] as [K] */
    fun deserialize(r: ByteRead): K
}

/** A boolean key */
object BooleanKeySerializer : KeySerializer<Boolean> {
    override fun serialize(w: ByteWrite, value: Boolean) {
        w.writeRawBE(if (value) 1L else 0L, 1)
    }
    override fun deserialize(r: ByteRead): Boolean {
        return r.readRawBE(1) != 0L
    }
}

/** Treats [Long] as unsigned. */
object UnsignedLongKeySerializer : KeySerializer<Long> {
    override fun serialize(w: ByteWrite, value: Long) {
        w.writeRawBE(value, 8)
    }

    override fun deserialize(r: ByteRead): Long {
        return r.readRawBE(8)
    }
}
/** Treats [Int] as unsigned. */
object UnsignedIntKeySerializer : KeySerializer<Int> {
    override fun serialize(w: ByteWrite, value: Int) {
        w.writeRawBE((value).toLong(), 4)
    }

    override fun deserialize(r: ByteRead): Int {
        return r.readRawBE(4).toInt()
    }
}

/**
 * Binary strings are compared like unsigned values,
 * but all common primitives are signed. So we add a bias.
 */
private const val BIAS32:Int = Int.MIN_VALUE // 0x8000_0000
private const val BIAS64:Long = Long.MIN_VALUE // 0x8000_0000_0000_0000

object IntKeySerializer : KeySerializer<Int> {
    override fun serialize(w: ByteWrite, value: Int) {
        w.writeRawBE((value + BIAS32).toLong(), 4)
    }

    override fun deserialize(r: ByteRead): Int {
        return r.readRawBE(4).toInt() - BIAS32
    }
}
object LongKeySerializer : KeySerializer<Long> {
    override fun serialize(w: ByteWrite, value: Long) {
        w.writeRawBE(value + BIAS64, 8)
    }

    override fun deserialize(r: ByteRead): Long {
        return r.readRawBE(8) - BIAS64
    }
}

private const val MASK32 = 0x7FFF_FFFF
private const val MASK64 = 0x7FFF_FFFF_FFFF_FFFFL
// https://en.wikipedia.org/wiki/IEEE_754-1985#Comparing_floating-point_numbers
object FloatKeySerializer : KeySerializer<Float> {
    override fun serialize(w: ByteWrite, value: Float) {
        val bits = value.toRawBits()
        val masked = bits and MASK32
        val biasedBits = if ((bits ushr 31) == 1) {
            // Negative, must be flipped
            MASK32 - masked
        } else {
            // Positive, must be added to bias
            BIAS32 + masked
        }
        w.writeRawBE(biasedBits.toLong(), 4)
    }

    override fun deserialize(r: ByteRead): Float {
        val bits = r.readRawBE(4).toInt()
        val masked = bits and MASK32
        val floatBits: Int = if ((bits ushr 31) == 0) {
            // Negative, unflip, add sign
            (MASK32 - masked) or BIAS32
        } else {
            // Positive, unbias (=masked)
            masked
        }
        return doubleToFloat(Float.fromBits(floatBits).toDouble())
    }
}

object DoubleKeySerializer : KeySerializer<Double> {
    override fun serialize(w: ByteWrite, value: Double) {
        val bits = value.toRawBits()
        val masked = bits and MASK64
        val biasedBits = if ((bits ushr 63).toInt() == 1) {
            // Negative, must be flipped
            MASK64 - masked
        } else {
            // Positive, must be added to bias
            BIAS64 + masked
        }
        w.writeRawBE(biasedBits, 8)
    }

    override fun deserialize(r: ByteRead): Double {
        val bits = r.readRawBE(8)
        val masked = bits and MASK64
        val floatBits: Long = if ((bits ushr 63).toInt() == 0) {
            // Negative, unflip, add sign
            (MASK64 - masked) or BIAS64
        } else {
            // Positive, unbias (=masked)
            masked
        }
        return Double.fromBits(floatBits)
    }
}

class EnumKeySerializer<T:Enum<T>>(private val values: Array<T>) : KeySerializer<T> {
    // https://stackoverflow.com/questions/1823346/whats-the-limit-to-the-number-of-members-you-can-have-in-a-java-enum
    // suggests that the max amount of enum members can't ever be more than 16bit and the actual limit is much smaller (but more than 256)
    // so using unsigned short seems fine
    override fun serialize(w: ByteWrite, value: T) {
        w.writeRawBE(value.ordinal.toLong(), 2)
    }

    override fun deserialize(r: ByteRead): T {
        return values[r.readRawBE(2).toInt()]
    }
}

/**
 * Encodes strings using UTF-8 encoding that supports round trip of unpaired surrogates ("WTF-8").
 * There are two variants, [TERMINATED] uses a null terminator and encodes 0 overlong, in two bytes,
 * and [UNTERMINATED] which reads until the end of the key.
 * Use the [TERMINATED] variant when compositing serializers and the [String] is not last.
 * This encoder should maintain the binary ordering of strings (with relation to the default, locale unaware ordering).
 * Invalid bytes in UTF-8 encoding (database corruption) are treated with an exception.
 */
class StringKeySerializer private constructor(private val nullTerminator: Boolean) : KeySerializer<String> {

    override fun deserialize(r: ByteRead): String {
        val sb = StringBuilder()

        while (r.canRead(1)) {
            val byte = r.readUnsignedByteOr(0)
            when {
                nullTerminator && byte == 0 -> break
                byte <= 0b01111111 -> {
                    sb.append(byte.toChar())
                    continue
                }
                byte and 0b111_00000 == 0b110_00000 -> {
                    val d1 = (byte and 0b000_11111) shl 6
                    val d2 = r.readContinuationByteData()
                    sb.append((d1 or d2).toChar())
                }
                byte and 0b1111_0000 == 0b1110_0000 -> {
                    val d1 = (byte and 0b0000_1111) shl 12
                    val d2 = r.readContinuationByteData() shl 6
                    val d3 = r.readContinuationByteData()
                    sb.append((d1 or d2 or d3).toChar())
                }
                byte and 0b11111_000 == 0b11110_000 -> {
                    val d1 = (byte and 0b0000_1111) shl 18
                    val d2 = r.readContinuationByteData() shl 12
                    val d3 = r.readContinuationByteData() shl 6
                    val d4 = r.readContinuationByteData()
                    val cp = d1 or d2 or d3 or d4

                    val high = (cp - 0x10000) shr 10 or 0xD800
                    val low = (cp and 0x3FF) or 0xDC00
                    sb.append(high.toChar())
                    sb.append(low.toChar())
                }
                else -> throw IllegalArgumentException("Expected UTF-8 start byte, got $byte")
            }
        }
        return sb.toString()
    }

    private fun ByteRead.readContinuationByteData(): Int {
        val b = readUnsignedByteOr(-1)
        if (b and 0b11_000000 != 0b10_000000) throw IllegalArgumentException("Expected continuation character, got $b")
        return b and 0b00_111111
    }

    private fun codePointFromSurrogate(string: String, high: Int, index: Int, endIndex: Int): Int {
        if (high !in 0xD800..0xDBFF || index >= endIndex) {
            return 0
        }
        val low = string[index].code
        if (low !in 0xDC00..0xDFFF) {
            return 0
        }
        return 0x10000 + ((high and 0x3FF) shl 10) or (low and 0x3FF)
    }

    override fun serialize(w: ByteWrite, value: String) {
        var charIndex = 0
        val endIndex = value.length

        while (charIndex < endIndex) {
            val code = value[charIndex++].code
            when {
                code < 0x80 && (code != 0 || !nullTerminator) ->
                    w.writeByte(code.toByte())
                code < 0x800 -> {
                    w.writeByte(((code shr 6) or 0xC0).toByte())
                    w.writeByte(((code and 0x3F) or 0x80).toByte())
                }
                code < 0xD800 || code >= 0xE000 -> {
                    w.writeByte(((code shr 12) or 0xE0).toByte())
                    w.writeByte((((code shr 6) and 0x3F) or 0x80).toByte())
                    w.writeByte(((code and 0x3F) or 0x80).toByte())
                }
                else -> { // Surrogate char value
                    val codePoint = codePointFromSurrogate(value, code, charIndex, endIndex)
                    if (codePoint <= 0) {
                        w.writeByte(((code shr 12) or 0xE0).toByte())
                        w.writeByte((((code shr 6) and 0x3F) or 0x80).toByte())
                        w.writeByte(((code and 0x3F) or 0x80).toByte())
                    } else {
                        w.writeByte(((codePoint shr 18) or 0xF0).toByte())
                        w.writeByte((((codePoint shr 12) and 0x3F) or 0x80).toByte())
                        w.writeByte((((codePoint shr 6) and 0x3F) or 0x80).toByte())
                        w.writeByte(((codePoint and 0x3F) or 0x80).toByte())
                        charIndex++
                    }
                }
            }
        }
        if (nullTerminator) {
            w.writeByte(0)
        }
    }

    companion object {
        val UNTERMINATED = StringKeySerializer(false)
        val TERMINATED = StringKeySerializer(true)
    }
}