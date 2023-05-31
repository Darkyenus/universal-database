package com.darkyen.database

import kotlin.math.min

actual class TableSet private constructor(
    internal val bits: IntArray,
    internal val tableNames: Array<String>
    ) {

    actual constructor(vararg tables: Table<*, *>) : this(IntArray(tables.maxOf { it.index shr 5 } + 1).also {
        for (table in tables) {
            val word = table.index shr 5
            val bit = table.index and 0x1F
            it[word] = it[word] or (1 shl bit)
        }
    }, Array<String>(tables.size) { tables[it].name })

    actual constructor(tables: Collection<Table<*, *>>) : this(IntArray(tables.maxOf { it.index shr 5 } + 1).also {
        for (table in tables) {
            val word = table.index shr 5
            val bit = table.index and 0x1F
            it[word] = it[word] or (1 shl bit)
        }
    }, Unit.run {
        val iterator = tables.iterator()
        Array<String>(tables.size) { iterator.next().name }
    })

    actual infix fun intersects(other: TableSet): Boolean {
        for (i in 0 until min(bits.size, other.bits.size)) {
            if ((bits[i] and other.bits[i]) != 0) return true
        }
        return false
    }

    actual fun isSubsetOf(superset: TableSet): Boolean {
        val subCount = bits.size
        val supCount = superset.bits.size

        if (subCount > supCount) {
            // We (subset) have more words, make sure that they are all 0
            for (i in supCount until subCount) {
                if (bits[i] != 0) return false
            }
        }

        for (i in 0 until min(subCount, supCount)) {
            val sub = bits[i]
            val sup = superset.bits[i]
            if (sub or sup != sup) return false
        }
        return true
    }

    actual operator fun contains(table: Table<*, *>): Boolean {
        val word = table.index shr 5
        if (word >= bits.size) return false
        val bit = table.index and 0x1F
        return (bits[word] and (1 shl bit)) != 0
    }
}