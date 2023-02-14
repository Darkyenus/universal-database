package com.darkyen.ud

import com.darkyen.ucbor.ByteData
import com.darkyen.ucbor.toHexString
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.internal.proptest
import io.kotest.property.resolution.GlobalArbResolver
import io.kotest.property.resolution.default
import kotlin.math.min
import kotlin.math.sign
import kotlin.reflect.typeOf

class DatabaseKeyTest : FunSpec({
    test("IntKeySerializer") {
        check(IntKeySerializer)
    }
    test("LongKeySerializer") {
        check(LongKeySerializer)
    }

    test("UnsignedIntKeySerializer") {
        check(UnsignedIntKeySerializer) { a, b -> a.toUInt().compareTo(b.toUInt()).sign }
    }
    test("UnsignedLongKeySerializer") {
        check(UnsignedLongKeySerializer) { a, b -> a.toULong().compareTo(b.toULong()).sign }
    }

    test("FloatKeySerializer") {
        check<Float>(FloatKeySerializer, arbitrary(listOf(
            Float.MAX_VALUE, Float.MIN_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN, Float.fromBits(0x003FFFFF), Float.fromBits(0x803FFFFF.toInt()), -1f, 1f, 0f, -0f,
            ).map { doubleToFloat(it.toDouble()) }, FloatShrinker) {
            doubleToFloat(Float.fromBits(it.random.nextInt()).toDouble())
        }) { a, b ->
            if (a.isNaN() || b.isNaN()) {
                // cthulhu ftahgn
                val aBits = a.toRawBits()
                val bBits = b.toRawBits()
                if (a.isNaN() && b.isNaN()) {
                    aBits.compareTo(bBits)
                } else if (a.isNaN()) {
                    if ((aBits ushr 31) == 1) {
                        // Negative NaN, lurking below the world
                        -1
                    } else {
                        // Positive NaN, watching over everything
                        1
                    }
                } else /*if (b.isNaN()) */{
                    if ((bBits ushr 31) == 1) {
                        // Negative NaN, lurking below the world
                        1
                    } else {
                        // Positive NaN, watching over everything
                        -1
                    }
                }
            } else {
                a.compareTo(b)
            }
        }
    }
    test("DoubleKeySerializer") {
        check(DoubleKeySerializer, arbitrary(listOf(
            Double.MAX_VALUE, Double.MIN_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN, Double.fromBits(0x0007FFFFFFFF3FFF), Double.fromBits(0x0007FFFFFFFFFFFF or (1L shl 63)), -1.0, 1.0, 0.0, -0.0,
        ), DoubleShrinker) {
            Double.fromBits(it.random.nextLong())
        }) { a, b ->
            if (a.isNaN() || b.isNaN()) {
                // cthulhu ftahgn
                val aBits = a.toRawBits()
                val bBits = b.toRawBits()
                if (a.isNaN() && b.isNaN()) {
                    aBits.compareTo(bBits)
                } else if (a.isNaN()) {
                    if ((aBits ushr 63) == 1L) {
                        // Negative NaN, lurking below the world
                        -1
                    } else {
                        // Positive NaN, watching over everything
                        1
                    }
                } else /*if (b.isNaN()) */{
                    if ((bBits ushr 63) == 1L) {
                        // Negative NaN, lurking below the world
                        1
                    } else {
                        // Positive NaN, watching over everything
                        -1
                    }
                }
            } else {
                a.compareTo(b)
            }
        }
    }

    test("EnumKeySerializer") {
        check(ConservationStatus.KEY_SERIALIZER, arb = Arb.of(*ConservationStatus.values()))
    }
})

@OptIn(ExperimentalKotest::class)
suspend inline fun <reified T:Comparable<T>> check(serializer: KeySerializer<T>, arb:Arb<T> = Arb.default<T>(), orderingArb:Arb<T> = arb, crossinline compare: (T, T) -> Int = { a, b -> a.compareTo(b).sign }) {
    val data = ByteData()
    withClue("roundtrip") {
        proptest(
            arb,
            PropTestConfig()
        ) { a ->
            data.resetForWriting(true)
            serializer.serialize(data, a)
            val recycledA = serializer.deserialize(data)
            withClue({"Serialized: ${data.toByteArray().toHexString()}"}) {
                compare(recycledA, a) shouldBe 0
            }
        }
    }

    withClue("ordering") {
        proptest(
            arb,
            arb,
            PropTestConfig()
        ) { a, b ->
            data.resetForWriting(true)
            serializer.serialize(data, a)
            val serializedA = data.toByteArray()

            data.resetForWriting(true)
            serializer.serialize(data, b)
            val serializedB = data.toByteArray()

            val correctOrdering = compare(a, b)
            val actualOrdering = compare(serializedA, serializedB)
            withClue({"Comparing $a with $b: ${serializedA.toHexString()} with ${serializedB.toHexString()}"}) {
                actualOrdering shouldBe correctOrdering
            }
        }
    }
}

fun compare(a: ByteArray, b: ByteArray):Int {
    val sharedSize = min(a.size, b.size)
    for (i in 0 until sharedSize) {
        val av = a[i].toInt() and 0xFF
        val bv = b[i].toInt() and 0xFF
        if (av != bv) {
            return if (av < bv) -1 else 1
        }
    }
    return a.size.compareTo(b.size).sign
}