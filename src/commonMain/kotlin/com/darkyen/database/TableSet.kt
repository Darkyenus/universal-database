package com.darkyen.database

/**
 * An immutable set of [Table]s.
 * Usually precomputed and cached for the runtime of the program.
 */
//TODO Turn into value class when Kotlin supports it
expect class TableSet {

    constructor(vararg tables: Table<*, *>)
    constructor(tables: Collection<Table<*, *>>)

    /**
     * Return whether there are any common bits set between this and [other]
     */
    infix fun intersects(other: TableSet): Boolean

    /**
     * Return whether all bits set on this are also set on [superset]
     */
    fun isSubsetOf(superset: TableSet): Boolean

    /**
     * Return whether the set contains [table]
     */
    operator fun contains(table: Table<*, *>): Boolean
}