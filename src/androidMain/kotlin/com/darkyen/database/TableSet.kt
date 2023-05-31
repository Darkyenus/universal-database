package com.darkyen.database

import kotlin.math.min

actual class TableSet private constructor(internal val words: IntArray) {
    actual constructor(vararg tables: Table<*, *>) : this(IntArray(tables.maxOf { it.index shr 5 } + 1).also {
        for (table in tables) {
            val word = table.index shr 5
            val bit = table.index and 0x1F
            it[word] = it[word] or (1 shl bit)
        }
    })

    actual constructor(tables: Collection<Table<*, *>>) : this(IntArray(tables.maxOf { it.index shr 5 } + 1).also {
        for (table in tables) {
            val word = table.index shr 5
            val bit = table.index and 0x1F
            it[word] = it[word] or (1 shl bit)
        }
    })

    actual infix fun intersects(other: TableSet): Boolean {
        for (i in 0 until min(words.size, other.words.size)) {
            if ((words[i] and other.words[i]) != 0) return true
        }
        return false
    }

    actual fun isSubsetOf(superset: TableSet): Boolean {
        val result = run {
            val subCount = words.size
            val supCount = superset.words.size

            if (subCount > supCount) {
                // We (subset) have more words, make sure that they are all 0
                for (i in supCount until subCount) {
                    if (words[i] != 0) return@run false
                }
            }

            for (i in 0 until min(subCount, supCount)) {
                val sub = words[i]
                val sup = superset.words[i]
                if (sub or sup != sup) return@run false
            }
            return@run true
        }
        println("  SUBSET: $this")
        println("SUPERSET: $superset")
        println(result)
        return result
    }

    actual operator fun contains(table: Table<*, *>): Boolean {
        val word = table.index shr 5
        if (word >= words.size) return false
        val bit = table.index and 0x1F
        return (words[word] and (1 shl bit)) != 0
    }

    override fun toString(): String {
        return buildString {
            append("TableSet(")
            for (word in words) {
                for (bit in 0 until 32) {
                    if ((word and (1 shl bit)) != 0) {
                        append('1')
                    } else {
                        append('0')
                    }
                }
            }
            append(")")
        }
    }
}