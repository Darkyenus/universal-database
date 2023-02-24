package com.darkyen.ud

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import android.text.TextUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import java.io.File

internal class SQLiteUniversalDatabase(private val dbHelper: SQLiteOpenHelper) : Database {
    private var closed = false
    private val dispatcher = ThreadStableDispatcher(2)
    private val mutex = ReadWriteMutex()

    override suspend fun <R> transaction(vararg usedTables: Table<*, *>, block: suspend Transaction.() -> R): Result<R> {
        if (closed) return Result.failure(IllegalStateException("Database is closed"))
        return runCatching {
            mutex.read {
                withContext(dispatcher + ThreadStableToken()) {
                    val db = dbHelper.readableDatabase
                    // Readable, no transaction necessary
                    block(SQLiteTransaction(db, usedTables))
                }
            }
        }
    }

    override suspend fun <R> writeTransaction(vararg usedTables: Table<*, *>, block: suspend WriteTransaction.() -> R): Result<R> {
        if (closed) return Result.failure(IllegalStateException("Database is closed"))
        return runCatching {
            mutex.write {
                withContext(dispatcher + ThreadStableToken()) {
                    val db = dbHelper.writableDatabase
                    db.sqlTransaction {
                        block(SQLiteTransaction(db, usedTables))
                    }
                }
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

        override fun checkWrite(): Boolean {
            return triggerChannel.tryReceive().isSuccess
        }

        override suspend fun awaitWrite() {
            triggerChannel.receive()
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

    override suspend fun <R> observeDatabaseWrites(vararg intoTables: Table<*, *>, block: DatabaseWriteObserver.() -> R):R {
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
        dbHelper.close()
        dispatcher.close()
    }
}

internal class SQLiteTransaction(private val db: SQLiteDatabase, private val tables: Array<out Table<*, *>>) : Transaction, WriteTransaction {

    private fun Query<*, *, *>.checkTables() {
        if (table !in tables) throw IllegalArgumentException("Can't query table $table, transaction uses only tables ${tables.contentToString()}")
    }
    private fun Table<*, *>.checkTables() {
        if (this !in tables) throw IllegalArgumentException("Can't query table $this, transaction uses only tables ${tables.contentToString()}")
    }

    override suspend fun Query<*, Nothing, *>.delete() {
        checkTables()
        val stat = db.compileStatement(buildString {
            append("DELETE FROM ").append(table.name).append(' ')
            this@delete.appendWhere(this)
        })
        this@delete.bindWhereParams(stat)

        stat.executeUpdateDelete()
        stat.close()
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.add(key: K, value: V) {
        checkTables()
        TODO("Not yet implemented")
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.set(key: K, value: V) {
        checkTables()
        TODO("Not yet implemented")
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.writeIterate(): Flow<MutableCursor<K, I, V>> {
        checkTables()
        TODO("Not yet implemented")
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.count(): Int {
        checkTables()

        val stat = db.compileStatement(buildString {
            append("SELECT COUNT(*) FROM ").append(table.name)
            appendWhere(this)
        })
        bindWhereParams(stat)

        val result = stat.simpleQueryForLong()
        stat.close()
        return result.toInt()
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getFirst(): V? {
        checkTables()
        val stat = db.compileStatement(buildString {
            append("SELECT $VALUE_COLUMN_NAME FROM ").append(table.name)
            appendWhere(this)
            appendOrder(this)
            append(" LIMIT 1")
        })
        bindWhereParams(stat)

        TODO("Not yet implemented")
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getFirstKey(): K? {
        checkTables()
        TODO("Not yet implemented")
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getAll(limit: Int): List<V> {
        checkTables()
        TODO("Not yet implemented")
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getAllKeys(limit: Int): List<K> {
        checkTables()
        TODO("Not yet implemented")
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterateKeys(): Flow<KeyCursor<K, I>> {
        checkTables()
        TODO("Not yet implemented")
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterate(): Flow<Cursor<K, I, V>> {
        checkTables()
        TODO("Not yet implemented")
    }
}

actual class Query<K : Any, I : Any, V : Any> internal constructor(
    internal val table: Table<K, V>,
    internal val index: Index<K, I, V>?,
    internal val minBound: ByteArray?,
    internal val minBoundOpen: Boolean,
    internal val maxBound: ByteArray?,
    internal val maxBoundOpen: Boolean,
    internal val increasing: Boolean,
) {

    val whereField: String
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
            sql.append("?1")

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
            sql.append("?2")
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

    fun bindWhereParams(stat: SQLiteStatement) {
        if (minBound != null) {
            stat.bindBlob(1, minBound)
        }
        if (maxBound != null) {
            stat.bindBlob(2, maxBound)
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

actual class BackendDatabaseConfig(
    name: String,
    vararg schema: Schema,
    val context: Context
): BaseDatabaseConfig(name, *schema)

actual suspend fun openUniversalDatabase(config: BackendDatabaseConfig): OpenDBResult {
    val schema = config.schema.last()
    val postMigrationCallbacks = ArrayList<() -> Unit>()
    var newerVersionExists = false
    val openHelper = object : SQLiteOpenHelper(config.context, config.name, null, schema.version) {
        override fun onCreate(db: SQLiteDatabase) {
            onUpgrade(db, 0, schema.version)
        }

        private fun createTableAndIndices(db: SQLiteDatabase, table: Table<*, *>) {
            db.execSQL(buildString {
                append("CREATE TABLE ? (? BLOB PRIMARY KEY NOT NULL, ? BLOB NOT NULL")
                for (index in table.indices) {
                    append(", ? BLOB NOT NULL")
                }
                append(")")
            }, Array(3 + table.indices.size) {
                when (it) {
                    0 -> table.name
                    1 -> KEY_COLUMN_NAME
                    2 -> VALUE_COLUMN_NAME
                    else -> table.indices[it-3].fieldName
                }
            })
            for (index in table.indices) {
                db.execSQL(if (index.unique) "CREATE UNIQUE INDEX ? ON ? (?)" else "CREATE INDEX ? ON ? (?)", arrayOf(index.canonicalName, table.name, index.fieldName))
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            val migrateFromIndex = config.schema.indexOfFirst { it.version == oldVersion }
            val transaction = SQLiteTransaction(db, schema.tables.toTypedArray())

            if (migrateFromIndex < 0) {
                // Create new version directly
                if (oldVersion != 0) {
                    // Delete all tables first
                    db.rawQuery("SELECT name FROM sqlite_schema WHERE type='table'", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            do {
                                val tableName = cursor.getString(0)
                                if (!tableName.startsWith("sqlite_")) {
                                    db.execSQL("DROP TABLE $tableName")
                                }
                            } while (cursor.moveToNext())
                        }
                    }
                }

                for (table in schema.tables) {
                    createTableAndIndices(db, table)
                }
                if (schema.createdNew != null) {
                    runBlocking {
                        schema.createdNew.invoke(transaction)
                    }
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
                            createTableAndIndices(db, table)
                        }
                    }
                    if (nextSchema.migrateFromPrevious != null) {
                        runBlocking {
                            nextSchema.migrateFromPrevious.invoke(transaction)
                        }
                    }
                    if (schema.afterSuccessfulCreationOrMigration != null) {
                        postMigrationCallbacks.add(schema.afterSuccessfulCreationOrMigration)
                    }
                    for (table in currentSchema.tables) {
                        if (table !in nextSchema.tables) {
                            db.execSQL("DROP TABLE ${table.name}")
                        }
                    }
                }
            }
        }

        override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            newerVersionExists = true
            super.onDowngrade(db, oldVersion, newVersion)
        }

        var callbackError: Throwable? = null
        override fun onOpen(db: SQLiteDatabase?) {
            for (callback in postMigrationCallbacks) {
                try {
                    callback()
                } catch (t: Throwable) {
                    val cbE = callbackError
                    if (cbE == null) {
                        callbackError = t
                    } else {
                        cbE.addSuppressed(t)
                    }
                }
            }
        }
    }

    try {
        openHelper.readableDatabase
    } catch (e:SQLiteException) {
        if (newerVersionExists) {
            return OpenDBResult.NewerVersionExists
        }
        if (e.message?.contains("Can't upgrade read-only database") == true) {
            return OpenDBResult.OutOfMemory
        }
        return OpenDBResult.Failure(e)
    }

    return OpenDBResult.Success(SQLiteUniversalDatabase(openHelper))
}

/** Performs [block] in an exclusive transaction. Rolls back on any exception. */
private inline fun <R> SQLiteDatabase.sqlTransaction(block: () -> R): R {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

private const val KEY_COLUMN_NAME = "k"
private const val VALUE_COLUMN_NAME = "v"

actual suspend fun deleteUniversalDatabase(config: BackendDatabaseConfig) {
    val filePath: File = config.context.getDatabasePath(config.name)
    if (!SQLiteDatabase.deleteDatabase(filePath) && filePath.exists()) {
        throw RuntimeException("Database ${config.name} still exists")
    }
}