package com.darkyen.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteFullException
import com.darkyen.sqlitelite.SQLiteConnection
import com.darkyen.sqlitelite.SQLiteStatement
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

internal class SQLiteUniversalDatabase(
    private val schema: Schema,
    primaryConnection: SQLiteConnection,
    parallelism: Int,
    openAnotherConnection: () -> SQLiteConnection
    ) : Database {

    private var closed = false
    private val connectionPool = LazyResourcePool(primaryConnection, (parallelism - 1).coerceAtLeast(0), openAnotherConnection)

    private suspend inline fun <R> withConnection(crossinline block: suspend (SQLiteConnection) -> R): Result<R> {
        return runCatching {
            withContext(Dispatchers.IO) {
                connectionPool.withResource { connection ->
                    block(connection)
                }
            }
        }
    }

    override suspend fun <R> transaction(vararg usedTables: Table<*, *>, block: suspend Transaction.() -> R): Result<R> {
        if (closed) return Result.failure(IllegalStateException("Database is closed"))
        if (usedTables.any { it !in schema.tables }) throw IllegalArgumentException("Transaction must use tables in schema")
        return withConnection { db ->
            try {
                db.beginTransactionDeferred()
                val result = block(SQLiteTransaction(db, usedTables))
                db.setTransactionSuccessful()
                result
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun <R> writeTransaction(vararg usedTables: Table<*, *>, block: suspend WriteTransaction.() -> R): Result<R> {
        if (closed) return Result.failure(IllegalStateException("Database is closed"))
        if (usedTables.any { it !in schema.tables }) throw IllegalArgumentException("Transaction must use tables in schema")
        return withConnection { db ->
            try {
                db.beginTransactionImmediate()
                val result = block(SQLiteTransaction(db, usedTables))
                db.setTransactionSuccessful()
                result
            } finally {
                db.endTransaction()
            }
        }.onSuccess {
            // Trigger listeners
            val trigger = ++lastObserverTrigger
            for (table in usedTables) {
                tableObservers[table]?.forEach { observer ->
                    if (observer.lastTrigger != trigger) {
                        observer.lastTrigger = trigger
                        observer.trigger()
                    }
                }
            }
        }
    }

    private class WriteObserver : DatabaseWriteObserver {
        var lastTrigger = 0
        val triggerChannel = Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override fun checkWrite(): WriteSource? {
            return if (triggerChannel.tryReceive().isSuccess) WriteSource.INTERNAL else null
        }

        override suspend fun awaitWrite(): WriteSource {
            triggerChannel.receive()
            return WriteSource.INTERNAL
        }

        fun trigger() {
            triggerChannel.trySend(Unit)
        }
    }

    private var lastObserverTrigger = 0
    private val tableObservers = HashMap<Table<*, *>, ArrayList<WriteObserver>>()

    override fun observeDatabaseWrites(scope: CoroutineScope, vararg intoTables: Table<*, *>): DatabaseWriteObserver {
        scope.ensureActive()
        val observer = WriteObserver()
        if (closed) return observer

        for (table in intoTables) {
            val list = tableObservers.getOrPut(table, ::ArrayList)
            list.add(observer)
        }
        scope.launch {
            try {
                awaitCancellation()
            } finally {
                for (table in intoTables) {
                    tableObservers[table]?.remove(observer)
                }
            }
        }
        return observer
    }

    override suspend fun <R> observeDatabaseWrites(vararg intoTables: Table<*, *>, block: suspend DatabaseWriteObserver.() -> R):R {
        val observer = WriteObserver()

        for (table in intoTables) {
            val list = tableObservers.getOrPut(table, ::ArrayList)
            list.add(observer)
        }
        try {
            return block(observer)
        } finally {
            for (table in intoTables) {
                tableObservers[table]?.remove(observer)
            }
        }
    }

    override fun close() {
        closed = true
        connectionPool.close()
    }
}

internal class SQLiteTransaction(private val db: SQLiteConnection, private val tables: Array<out Table<*, *>>) : Transaction, WriteTransaction {

    private fun Query<*, *, *>.checkTables() {
        if (table !in tables) throw IllegalArgumentException("Can't query table $table, transaction uses only tables ${tables.contentToString()}")
    }
    private fun Table<*, *>.checkTables() {
        if (this !in tables) throw IllegalArgumentException("Can't query table $this, transaction uses only tables ${tables.contentToString()}")
    }

    private inline fun <R> withStatement(buildSql: StringBuilder.() -> Unit, block: (SQLiteStatement) -> R): R {
        val sql = StringBuilder()
        sql.buildSql()
        try {
            return db.statement(sql.toString()).use(block)
        } catch (e: SQLiteConstraintException) {
            throw ConstraintException(e)
        }
    }

    override suspend fun Query<*, Nothing, *>.delete() {
        checkTables()
        withStatement({
            append("DELETE FROM ").append(table.name)
            appendWhere(this)
        }) { stat ->
            bindWhereParams(stat)
            stat.executeForNothing()
        }
    }

    private fun <K : Any, V : Any, I:Any> serializeIndex(key: K, value: V, index: Index<K, I, V>): ByteArray {
        return serialize(index.indexSerializer, index.indexExtractor(key, value))
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.add(key: K, value: V) {
        checkTables()
        withStatement({
            append("INSERT OR ABORT INTO ").append(name).append(" ($KEY_COLUMN_NAME, $VALUE_COLUMN_NAME")
            for (index in this@add.indices) {
                append(", ").append(index.fieldName)
            }
            append(") VALUES (?1, ?2")
            for (i in this@add.indices.indices) {
                append(", ?").append(3 + i)
            }
            append(")")
        }) { stat ->
            stat.bind(1, serialize(keySerializer, key))
            stat.bind(2, serialize(valueSerializer, value))
            for ((i, index) in this@add.indices.withIndex()) {
                stat.bind(3 + i, serializeIndex(key, value, index))
            }
            stat.executeForNothing()
        }
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.set(key: K, value: V) {
        checkTables()
        withStatement({
            append("INSERT INTO ").append(name).append(" ($KEY_COLUMN_NAME, $VALUE_COLUMN_NAME")
            for (index in this@set.indices) {
                append(", ").append(index.fieldName)
            }
            append(") VALUES (?1, ?2")
            for (i in this@set.indices.indices) {
                append(", ?").append(3 + i)
            }
            append(") ON CONFLICT ($KEY_COLUMN_NAME) DO UPDATE SET $VALUE_COLUMN_NAME = ?2")
            for ((i, index) in this@set.indices.withIndex()) {
                append(", ").append(index.fieldName).append(" = ").append("?").append(3 + i)
            }
        }) { stat ->
            stat.bind(1, serialize(keySerializer, key))
            stat.bind(2, serialize(valueSerializer, value))
            for ((i, index) in this@set.indices.withIndex()) {
                stat.bind(3 + i, serializeIndex(key, value, index))
            }
            stat.executeForNothing()
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.count(): Int {
        checkTables()
        return withStatement({
            append("SELECT COUNT(*) FROM ").append(table.name)
            appendWhere(this)
        }) { stat ->
            bindWhereParams(stat)
            stat.executeForLong(0).toInt()
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getFirst(): V? {
        checkTables()
        return withStatement({
            append("SELECT $VALUE_COLUMN_NAME FROM ").append(table.name)
            appendWhere(this)
            appendOrder(this)
            append(" LIMIT 1")
        }) { stat ->
            bindWhereParams(stat)
            if (stat.cursorNextRow()) {
                deserialize(table.valueSerializer, stat.cursorGetBlob(0) ?: return@withStatement null)
            } else null
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getFirstKey(): K? {
        checkTables()
        return withStatement({
            append("SELECT $KEY_COLUMN_NAME FROM ").append(table.name)
            appendWhere(this)
            appendOrder(this)
            append(" LIMIT 1")
        }) { stat ->
            bindWhereParams(stat)
            if (stat.cursorNextRow()) {
                deserialize(table.keySerializer, stat.cursorGetBlob(0)!!)
            } else null
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getAll(limit: Int): List<V> {
        checkTables()
        return withStatement({
            append("SELECT $VALUE_COLUMN_NAME FROM ").append(table.name)
            appendWhere(this)
            appendOrder(this)
            appendLimit(this, limit)
        }) { s ->
            bindWhereParams(s)
            if (!s.cursorNextRow()) return@withStatement emptyList()
            val result = ArrayList<V>()
            do {
                result.add(deserialize(table.valueSerializer, s.cursorGetBlob(0)!!))
            } while (s.cursorNextRow())
            result
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getAllKeys(limit: Int): List<K> {
        checkTables()
        return withStatement({
            append("SELECT $KEY_COLUMN_NAME FROM ").append(table.name)
            appendWhere(this)
            appendOrder(this)
            appendLimit(this, limit)
        }) { s ->
            bindWhereParams(s)
            if (!s.cursorNextRow()) return@withStatement emptyList()
            val result = ArrayList<K>()
            do {
                result.add(deserialize(table.keySerializer, s.cursorGetBlob(0)!!))
            } while (s.cursorNextRow())
            result
        }
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterateKeys(): Flow<KeyCursor<K, I>> {
        checkTables()
        return flow {
            withStatement({
                append("SELECT $KEY_COLUMN_NAME")
                if (index != null) append(", ").append(index.fieldName)
                append(" FROM ").append(table.name)
                appendWhere(this)
                appendOrder(this)
            }) { s ->
                bindWhereParams(s)
                if (!s.cursorNextRow()) return@withStatement
                val outCursor = object : KeyCursor<K, I> {
                    override val key: K
                        get() = deserialize(table.keySerializer, s.cursorGetBlob(0)!!)
                    override val indexKey: I
                        get() = if (index != null) deserialize(index.indexSerializer, s.cursorGetBlob(1)!!) else throw NoSuchElementException("The cursor has no index")
                }
                do {
                    emit(outCursor)
                } while (s.cursorNextRow())
            }
        }
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterate(): Flow<Cursor<K, I, V>> {
        checkTables()
        return flow {
            withStatement({
                append("SELECT $KEY_COLUMN_NAME, $VALUE_COLUMN_NAME")
                if (index != null) append(", ").append(index.fieldName)
                append(" FROM ").append(table.name)
                appendWhere(this)
                appendOrder(this)
            }) { s ->
                bindWhereParams(s)
                if (!s.cursorNextRow()) return@withStatement
                val outCursor = object : Cursor<K, I, V> {
                    override val key: K
                        get() = deserialize(table.keySerializer, s.cursorGetBlob(0)!!)
                    override val value: V
                        get() = deserialize(table.valueSerializer, s.cursorGetBlob(1)!!)
                    override val indexKey: I
                        get() = if (index != null) deserialize(index.indexSerializer, s.cursorGetBlob(2)!!) else throw NoSuchElementException("The cursor has no index")
                }
                do {
                    emit(outCursor)
                } while (s.cursorNextRow())
            }
        }
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.writeIterate(): Flow<MutableCursor<K, I, V>> {
        checkTables()
        return flow {
            withStatement({
                append("SELECT $KEY_COLUMN_NAME, $VALUE_COLUMN_NAME")
                if (index != null) append(", ").append(index.fieldName)
                append(" FROM ").append(table.name)
                appendWhere(this)
                appendOrder(this)
            }) { s ->
                bindWhereParams(s)
                if (!s.cursorNextRow()) return@withStatement
                val outCursor = object : MutableCursor<K, I, V> {
                    override val key: K
                        get() = deserialize(table.keySerializer, s.cursorGetBlob(0)!!)
                    override val value: V
                        get() = deserialize(table.valueSerializer, s.cursorGetBlob(1)!!)
                    override val indexKey: I
                        get() = if (index != null) deserialize(index.indexSerializer, s.cursorGetBlob(2)!!) else throw NoSuchElementException("The cursor has no index")

                    override suspend fun update(newValue: V) {
                        table.set(key, newValue)
                    }
                    override suspend fun delete() {
                        table.queryOne(key).delete()
                    }
                }
                do {
                    emit(outCursor)
                } while (s.cursorNextRow())
            }
        }
    }
}

actual class Query<K : Any, I : Any, V : Any> internal constructor(
    internal val table: Table<K, V>,
    internal val index: Index<K, I, V>?,
    private val minBound: ByteArray?,
    private val minBoundOpen: Boolean,
    private val maxBound: ByteArray?,
    private val maxBoundOpen: Boolean,
    private val increasing: Boolean,
) {

    private val whereField: String
        get() = index?.fieldName ?: KEY_COLUMN_NAME

    fun appendWhere(sql: StringBuilder) {
        if (minBound == null && maxBound == null) {
            return
        }
        sql.append(" WHERE ")

        val field = whereField
        if (minBound != null) {
            sql.append(field)
            if (minBoundOpen) {
                sql.append(" > ")
            } else {
                sql.append(" >= ")
            }
            sql.append("?")

            if (maxBound != null) {
                sql.append(" AND ")
            }
        }

        if (maxBound != null) {
            sql.append(field)
            if (maxBoundOpen) {
                sql.append(" < ")
            } else {
                sql.append(" <= ")
            }
            sql.append("?")
        }
    }

    fun appendOrder(sql: StringBuilder) {
        sql.append(" ORDER BY ").append(whereField)
        if (increasing) {
            sql.append(" ASC")
        } else {
            sql.append(" DESC")
        }
    }

    fun appendLimit(sql: StringBuilder, limit: Int) {
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit)
        }
    }

    fun bindWhereParams(stat: SQLiteStatement) {
        var nextIndex = 1
        if (minBound != null) {
            stat.bind(nextIndex++, minBound)
        }
        if (maxBound != null) {
            stat.bind(nextIndex, maxBound)
        }
    }
}

actual fun <K : Any, V : Any> Table<K, V>.queryAll(increasing: Boolean): Query<K, Nothing, V> {
    return Query(this, null, null, false, null, false, increasing)
}
actual fun <K : Any, V : Any> Table<K, V>.queryOne(value: K): Query<K, Nothing, V> {
    val serialized = serialize(keySerializer, value)
    return Query(this, null, serialized, false, serialized, maxBoundOpen = false, increasing = true)
}
actual fun <K : Any, V : Any> Table<K, V>.query(min: K?, max: K?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, Nothing, V> {
    val serializedMin = min?.let { serialize(keySerializer, min) }
    val serializedMax = if (min === max) serializedMin else max?.let { serialize(keySerializer, max) }
    return Query(this, null, serializedMin, openMin, serializedMax, openMax, increasing)
}
actual fun <K: Any, I:Any, V: Any> Index<K, I, V>.query(min: I?, max: I?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, I, V> {
    val serializedMin = min?.let { serialize(indexSerializer, min) }
    val serializedMax = if (min === max) serializedMin else max?.let { serialize(indexSerializer, max) }
    return Query(this.table, this, serializedMin, openMin, serializedMax, openMax, increasing)
}

private const val DB_OPEN_FLAGS = SQLiteConnection.SQLITE_OPEN_CREATE or SQLiteConnection.SQLITE_OPEN_READWRITE

actual class BackendDatabaseConfig(
    name: String,
    vararg schema: Schema,
    val context: Context,
    val parallelism: Int = 2
): BaseDatabaseConfig(name, *schema)

actual suspend fun openUniversalDatabase(config: BackendDatabaseConfig): OpenDBResult {
    val schema = config.schema.last()
    val postMigrationCallbacks = ArrayList<() -> Unit>()

    val databaseFile = config.context.getDatabasePath(config.name)
    val dbPath = databaseFile.absolutePath
    val db = SQLiteConnection.open(dbPath, DB_OPEN_FLAGS)
    try {
        db.pragma("PRAGMA journal_mode=wal")
        val currentVersion = db.pragma("PRAGMA user_version")?.toIntOrNull() ?: 0
        val targetVersion: Int = schema.version

        if (currentVersion > targetVersion) {
            db.close()
            return OpenDBResult.NewerVersionExists
        }

        fun createTableAndIndices(table: Table<*, *>) {
            db.command(buildString {
                append("CREATE TABLE ").append(table.name).append(" ($KEY_COLUMN_NAME BLOB PRIMARY KEY NOT NULL, $VALUE_COLUMN_NAME BLOB NOT NULL")
                for (index in table.indices) {
                    append(", ").append(index.fieldName).append(" BLOB NOT NULL")
                }
                append(")")
            })
            for (index in table.indices) {
                db.command("CREATE${if (index.unique) " UNIQUE" else ""} INDEX ${index.canonicalName} ON ${table.name} (${index.fieldName})")
            }
        }

        if (currentVersion < targetVersion) {
            db.beginTransactionExclusive()
            try {
                val migrateFromIndex = config.schema.indexOfFirst { it.version == currentVersion }

                if (migrateFromIndex < 0) {
                    // Create new version directly
                    if (currentVersion != 0) {
                        // Delete all tables first
                        db.statement("SELECT name FROM sqlite_schema WHERE type='table'").use { s ->
                            while (s.cursorNextRow()) {
                                val tableName = s.cursorGetString(0) ?: continue
                                if (!tableName.startsWith("sqlite_")) {
                                    db.command("DROP TABLE $tableName")
                                }
                            }
                        }
                    }

                    for (table in schema.tables) {
                        createTableAndIndices(table)
                    }
                    if (schema.createdNew != null) {
                        schema.createdNew.invoke(SQLiteTransaction(db, schema.tables.toTypedArray()))
                    }
                    if (schema.afterSuccessfulCreationOrMigration != null) {
                        postMigrationCallbacks.add(schema.afterSuccessfulCreationOrMigration)
                    }
                } else {
                    for (nextSchemaIndex in migrateFromIndex + 1 until config.schema.size) {
                        val currentSchema = config.schema[nextSchemaIndex - 1]
                        val nextSchema = config.schema[nextSchemaIndex]

                        for (table in nextSchema.tables) {
                            if (table !in currentSchema.tables) {
                                createTableAndIndices(table)
                            }
                        }
                        if (nextSchema.migrateFromPrevious != null) {
                            nextSchema.migrateFromPrevious.invoke(SQLiteTransaction(db, (currentSchema.tables + nextSchema.tables).toTypedArray()))
                        }
                        if (nextSchema.afterSuccessfulCreationOrMigration != null) {
                            postMigrationCallbacks.add(nextSchema.afterSuccessfulCreationOrMigration)
                        }
                        for (table in currentSchema.tables) {
                            if (table !in nextSchema.tables) {
                                db.command("DROP TABLE ${table.name}")
                            }
                        }
                    }
                }
                db.pragma("PRAGMA user_version=$targetVersion")

                for (callback in postMigrationCallbacks) {
                    callback()
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    } catch (e: Throwable) {
        try {
            db.close()
        } catch (s: Throwable) {
            e.addSuppressed(s)
        }
        if (e is SQLiteFullException) {
            return OpenDBResult.OutOfMemory
        }
        return OpenDBResult.Failure(e)
    }

    return OpenDBResult.Success(SQLiteUniversalDatabase(schema, db, config.parallelism) {
        val secondary = SQLiteConnection.open(dbPath, DB_OPEN_FLAGS)

        try {
            secondary.pragma("PRAGMA journal_mode=wal")
        } catch (e: Throwable) {
            try {
                secondary.close()
            } catch (s: Throwable) {
                e.addSuppressed(s)
            }
            throw e
        }
        secondary
    })
}

private const val KEY_COLUMN_NAME = "k"
private const val VALUE_COLUMN_NAME = "v"

actual suspend fun deleteUniversalDatabase(config: BackendDatabaseConfig) {
    val filePath: File = config.context.getDatabasePath(config.name)
    if (!SQLiteDatabase.deleteDatabase(filePath) && filePath.exists()) {
        throw RuntimeException("Database ${config.name} still exists")
    }
}