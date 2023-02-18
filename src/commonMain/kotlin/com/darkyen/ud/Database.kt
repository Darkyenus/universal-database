package com.darkyen.ud

import com.darkyen.ucbor.CborSerializer
import kotlinx.coroutines.flow.Flow

interface Database {
    suspend fun <R> transaction(vararg usedTables: Table<*, *>, block: suspend Transaction.() -> R): Result<R>
    suspend fun <R> writeTransaction(vararg usedTables: Table<*, *>, block: suspend WriteTransaction.() -> R): Result<R>
    fun close()
}

open class BaseDatabaseConfig(
    /** Database name */
    val name: String,
    /** Last schema is the main one, previous are the evolution versions.
     *  Versions must be strictly increasing by 1. */
    vararg val schema: Schema,
) {
    init {
        validateName(name)
        validateSchemaSequence(schema)
    }
}

/** Thrown when attempting to put data into the database that would create duplicates where duplicates are not allowed. */
class ConstraintException(message: String) : RuntimeException(message)
/** Thrown when the storage is full and will not take any more data */
class QuotaException(message: String) : RuntimeException(message)

interface KeyCursor<K:Any, I: Any> {
    val key: K
    val indexKey: I
}

interface Cursor<K:Any, I:Any, V:Any> : KeyCursor<K, I> {
    val value: V
}

interface MutableCursor<K:Any, I:Any, V:Any> : Cursor<K, I, V> {
    /**
     * @throws ConstraintException when unique index constraint would be violated by this change
     */
    suspend fun update(newValue: V)
    suspend fun delete()
}

interface Transaction {
    suspend fun <K:Any, I:Any, V:Any> Query<K, I, V>.count(): Int
    suspend fun <K:Any, I:Any, V:Any> Query<K, I, V>.getFirst(): V?
    suspend fun <K:Any, I:Any, V:Any> Query<K, I, V>.getFirstKey(): K?
    suspend fun <K:Any, I:Any, V:Any> Query<K, I, V>.getAll(limit: Int = 0): List<V>
    suspend fun <K:Any, I:Any, V:Any> Query<K, I, V>.getAllKeys(limit: Int = 0): List<K>
    fun <K:Any, I:Any, V:Any> Query<K, I, V>.iterateKeys(): Flow<KeyCursor<K, I>>
    fun <K:Any, I:Any, V:Any> Query<K, I, V>.iterate(): Flow<Cursor<K, I, V>>
}

interface WriteTransaction : Transaction {
    suspend fun Query<*, Nothing, *>.delete()
    /**
     * @throws ConstraintException when entry with [key] already exists or when unique index constraint would be violated by this change
     * @throws QuotaException
     */
    suspend fun <K:Any, V:Any> Table<K, V>.add(key: K, value: V)
    /**
     * @throws ConstraintException when unique index constraint would be violated by this change
     * @throws QuotaException
     */
    suspend fun <K:Any, V:Any> Table<K, V>.set(key: K, value: V)
    fun <K:Any, I:Any, V:Any> Query<K, I, V>.writeIterate(): Flow<MutableCursor<K, I, V>>
}

/**
 * Describes a table that holds data.
 * Values in a table are serialized using [valueSerializer].
 * Keys are ordered, based on their binary representation and are serialized using [keySerializer].
 * Key-value pairs can be also looked up through [indices] which extract some value from the key-value through which the lookup is done.
 */
class Table<Key:Any, Value:Any>(
    val name: String,
    internal val keySerializer: KeySerializer<Key>,
    internal val valueSerializer: CborSerializer<Value>,
    internal val indices: List<Index<Key, *, Value>> = emptyList()
) {
    init {
        validateName(name)
        for ((i, index) in indices.withIndex()) {
            if (index.internalTable != null) throw IllegalStateException("Index is already a part of a table")
            index.internalTable = this
            index.canonicalName = name+"_"+index.name
            index.fieldName = "i$i"
        }
    }

    override fun toString(): String {
        return "Table $name"
    }
}

class Index<Key:Any, Index:Any, Value:Any>(
    val name: String,
    internal val indexExtractor: (Key, Value) -> Index,
    internal val indexSerializer: KeySerializer<Index>,
    internal val unique: Boolean
) {
    init {
        validateName(name)
    }
    internal lateinit var canonicalName: String
    internal lateinit var fieldName: String

    internal var internalTable: Table<Key, Value>? = null
    val table: Table<Key, Value>
        get() = internalTable ?: throw IllegalStateException("Index is not a part of any table")

    override fun toString(): String {
        return "Index $canonicalName"
    }
}

/**
 * Describes a database schema [version].
 * When migrating schema versions, only upgrades by 1 are allowed.
 *
 * All [tables] and indices ([Table.indices]) that share name must be refrentially identical between versions.
 * Tables/indices that are not in the old version will be created,
 * then [migrateFromPrevious] is called and then tables/indices that are not in this version are deleted.
 */
class Schema(
    internal val version: Int,
    internal val tables: List<Table<*, *>>,
    internal val migrateFromPrevious: (suspend WriteTransaction.() -> Unit)? = null,
    internal val createdNew: (suspend WriteTransaction.() -> Unit)? = null,
    /** Called when database opening during which [migrateFromPrevious] or [createdNew] of this schema was called */
    internal val afterSuccessfulCreationOrMigration: (() -> Unit)? = null
)

/** Describes a set of objects in a database. */
expect class Query<Key:Any, Index:Any, Value: Any>

expect fun <K:Any, V:Any> Table<K, V>.queryAll(increasing: Boolean = true): Query<K, Nothing, V>
expect fun <K:Any, V:Any> Table<K, V>.queryOne(value: K): Query<K, Nothing, V>
expect fun <K: Any, V: Any> Table<K, V>.query(min: K?, max: K?, openMin: Boolean = false, openMax: Boolean = false, increasing: Boolean = true): Query<K, Nothing, V>
expect fun <K: Any, I:Any, V: Any> Index<K, I, V>.query(min: I?, max: I?, openMin: Boolean = false, openMax: Boolean = false, increasing: Boolean = true): Query<K, I, V>


/**
 * Returned by [openUniversalDatabase]
 */
sealed class OpenDBResult {
    /** Database [db] opened successfully */
    class Success(val db: Database) : OpenDBResult()
    /** Database can't be opened, because the storage already contains a newer version */
    object NewerVersionExists : OpenDBResult()
    /** Database can't be opened, because the platform does not support the underlying technology */
    class StorageNotSupported(val reason: String) : OpenDBResult()
    /** Database can't be opened, because there is not enough memory to do so */
    object OutOfMemory : OpenDBResult()
    /** Some other error has occurred, such as IO problem */
    object UnknownError : OpenDBResult()
    /** Database failed to open due to an [exception] */
    class Failure(val exception: Throwable) : OpenDBResult()
}

/** Concrete configuration for the specific backend. */
expect class BackendDatabaseConfig : BaseDatabaseConfig

/**
 * Attempt to open a database using this backend.
 */
expect suspend fun openUniversalDatabase(config: BackendDatabaseConfig): OpenDBResult

/**
 * Attempt to completely delete database described by [config] and presumably already created.
 * The database must not be open.
 * @return if the database does not exist now (or didn't exist before)
 * @throws Exception if the database still exists
 */
expect suspend fun deleteUniversalDatabase(config: BackendDatabaseConfig)

