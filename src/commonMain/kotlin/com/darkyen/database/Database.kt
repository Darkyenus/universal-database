package com.darkyen.database

import com.darkyen.cbor.CborSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmInline
import kotlin.math.max
import kotlin.math.min

interface Database {
    /**
     * Perform a read-transaction, which is allowed to read only [usedTables].
     * Unlimited amount of read transactions can run in parallel.
     * While any read transactions are running, no [writeTransaction]s can run on the same tables.
     * Try to keep the duration of the transactions short.
     * Some backends may not allow to perform any other suspending functions than those related to the [Transaction].
     */
    suspend fun <R> transaction(usedTables: TableSet, block: suspend Transaction.() -> R): Result<R>

    @Deprecated("Precompute the table set", ReplaceWith("transaction(TableSet(usedTables), block)"))
    suspend fun <R> transaction(vararg usedTables: Table<*, *>, block: suspend Transaction.() -> R): Result<R> {
        return transaction(TableSet(*usedTables), block)
    }

    /**
     * Perform a write-transaction, which is allowed to read and write only [usedTables].
     * Only one write transaction can run on the same table at the same time
     * (however the transaction may start with the first request, so this function does not necessarily work as a mutex).
     * When [block] throws an exception, the transaction is rolled back.
     * As in [transaction], try to keep the transaction as short as possible and note that non-transaction
     * suspending functions may not be allowed in some backends.
     *
     * @return result from [block] or an exception (this should not throw anything)
     * @throws IllegalStateException when the database is closed
     */
    suspend fun <R> writeTransaction(usedTables: TableSet, block: suspend WriteTransaction.() -> R): Result<R>

    @Deprecated("Precompute the table set", ReplaceWith("writeTransaction(TableSet(usedTables), block)"))
    suspend fun <R> writeTransaction(vararg usedTables: Table<*, *>, block: suspend WriteTransaction.() -> R): Result<R> {
        return writeTransaction(TableSet(*usedTables), block)
    }

    /**
     * Create a [DatabaseWriteObserver] that will be triggered after each successful [writeTransaction]
     * into any of the tables in [intoTables].
     * You can create as many observers on the same table as you want.
     * @param scope when this scope closes, the observer will unregister itself and close other held resources. The scope must be active at the time of this call.
     */
    fun observeDatabaseWrites(scope: CoroutineScope, intoTables: TableSet): DatabaseWriteObserver

    @Deprecated("Precompute the table set", ReplaceWith("observeDatabaseWrites(scope, TableSet(intoTables))"))
    fun observeDatabaseWrites(scope: CoroutineScope, vararg intoTables: Table<*, *>): DatabaseWriteObserver {
        return observeDatabaseWrites(scope, TableSet(*intoTables))
    }

    /**
     * Variant without [CoroutineScope] argument for short running actions. The [DatabaseWriteObserver] is valid and registered only until [block] does not end.
     */
    suspend fun <R> observeDatabaseWrites(intoTables: TableSet, block: suspend DatabaseWriteObserver.() -> R):R

    @Deprecated("Precompute the table set", ReplaceWith("observeDatabaseWrites(TableSet(intoTables), block)"))
    suspend fun <R> observeDatabaseWrites(vararg intoTables: Table<*, *>, block: suspend DatabaseWriteObserver.() -> R):R {
        return observeDatabaseWrites(TableSet(*intoTables), block)
    }

    /**
     * Close the database.
     * Note that database can become closed even without calling this function,
     * for example due to a hardware failure.
     */
    fun close()
}

/**
 * Contains a single boolean flag that indicates
 * whether the database has been modified since the last check.
 * Check operation atomically returns the flag value and sets it to false (no write).
 * The flag starts in "no write" state.
 * The API is meant to be used by a single consumer.
 */
interface DatabaseWriteObserver {
    /**
     * Check the write-flag, return its value and reset it.
     * @return source of the write or null when no write has occurred
     */
    fun checkWrite(): WriteSource?

    /**
     * Check the write-flag, return if it is true and set it to false.
     * Otherwise, suspend until the write-flag is set to true, then set it to false and return.
     */
    suspend fun awaitWrite(): WriteSource
}

enum class WriteSource {
    /** This process did write */
    INTERNAL,
    /** Different process did write */
    EXTERNAL,
    /** Since last check, there were writes from both this and external processes */
    INTERNAL_AND_EXTERNAL
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
class ConstraintException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}
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

private var nextTableIndex = 0

/**
 * Describes a table that holds data.
 * Values in a table are serialized using [valueSerializer].
 * Keys are ordered, based on their binary representation and are serialized using [keySerializer].
 * Key-value pairs can be also looked up through [indices] which extract some value from the key-value through which the lookup is done.
 */
class Table<Key:Any, Value:Any> constructor(
    val name: String,
    internal val keySerializer: KeySerializer<Key>,
    internal val valueSerializer: CborSerializer<Value>,
    internal val indices: List<Index<Key, *, Value>> = emptyList()
) {

    internal val index = nextTableIndex++

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
) {
    internal val tableSet = TableSet(tables)
}

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

