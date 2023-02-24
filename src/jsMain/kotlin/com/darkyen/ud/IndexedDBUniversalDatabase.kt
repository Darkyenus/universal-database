package com.darkyen.ud

import com.juul.indexeddb.external.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.*

internal suspend inline fun <T:IndexedDBTransaction, R> runTransaction(transaction: T, upgrade:Boolean, noinline block: suspend T.() -> R): Result<R> {
    return withContext(Dispatchers.Unconfined) {
        coroutineScope {
            val trans = transaction.transaction
            val parentJob = coroutineContext[Job]!!
            val transactionJob = CompletableDeferred<Boolean/*Completed*/>(parentJob)

            trans.oncomplete = {
                transactionJob.complete(true)
            }
            trans.onabort = {
                it.preventDefault()
                it.stopPropagation()
                transactionJob.complete(false)
            }
            trans.onerror = {
                console.info("!!!! Uncaught error in transaction !!!!")
                it.preventDefault()
                it.stopPropagation()
                trans.abort()
                parentJob.cancel("Uncaught error in transaction", reinterpretException(trans.error))
            }

            val result = try {
                val result = transaction.block()
                Result.success(result)
            } catch (e: dynamic) {
                Result.failure(reinterpretException(e))
            }

            ensureActive()// We might have been cancelled by an uncaught error
            // If all goes well, the transaction should still be running
            val completedTooSoon = transactionJob.isCompleted

            // It should finish after a while
            if (!completedTooSoon) {
                if (upgrade) {
                    // Do not wait for complete event, Chrome does not send it for upgrade transaction. Spec does not seem to allow it, but nobody complains.
                    trans.asDynamic().oncomplete = null// Clear listener, because Firefox *does* send complete event
                    transactionJob.complete(true)
                } else if (result.isSuccess) {
                    if (trans.asDynamic().commit.unsafeCast<Boolean>()) {
                        trans.commit()
                    }
                } else {
                    trans.abort()
                }
            }

            val complete = transactionJob.await()
            if (complete) {
                if (completedTooSoon && result.isSuccess) {
                    val e = IllegalStateException("Transaction completed before block, this indicates incorrect use of suspend functions")
                    if (trans.error != null) {
                        e.addSuppressed(reinterpretException(trans.error))
                    }
                    Result.failure(e)
                } else {
                    // All ok
                    result
                }
            } else {
                // Aborted
                if (trans.error == null) {
                    // Aborted manually through abort() above
                    result.addSuppressed(RuntimeException("Transaction aborted due to block error"))
                } else {
                    result.addSuppressed(RuntimeException("Transaction aborted", reinterpretException(trans.error)))
                }
            }
        }
    }
}

/** When true, transaction errors will include the original calling site of the transaction and all internal exception will be logged. */
var DEBUG = false

internal class IndexedDBUniversalDatabase(
    private val db: IDBDatabase
) : Database {

    internal var closed = false

    override suspend fun <R> transaction(vararg usedTables: Table<*, *>, block: suspend Transaction.() -> R): Result<R> {
        if (closed) return Result.failure(IllegalStateException("Database is closed"))
        return reinterpretExceptions(if (DEBUG) Exception("transaction started here") else null) {
            val tables = Array(usedTables.size) { usedTables[it].name }
            val trans = db.transaction(tables, "readonly")
            val transaction = IndexedDBTransaction(trans)
            runTransaction(transaction, false, block)
        }
    }

    override suspend fun <R> writeTransaction(vararg usedTables: Table<*, *>, block: suspend WriteTransaction.() -> R): Result<R> {
        if (closed) return Result.failure(IllegalStateException("Database is closed"))
        return reinterpretExceptions(if (DEBUG) Exception("writeTransaction started here") else null) {
            val tables = Array(usedTables.size) { usedTables[it].name }
            val trans = db.transaction(tables, "readwrite")
            val transaction = IndexedDBWriteTransaction(trans)
            val result = runTransaction(transaction, false, block)
            result.onSuccess {
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
            result
        }
    }

    override fun close() {
        this.closed = true
        db.close()
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
}

internal suspend fun <T> IDBRequest<T>.result():T {
    return withContext(Dispatchers.Unconfined) {
         suspendCancellableCoroutine { cont ->
            onsuccess = {
                cont.resume(this@result.result)
            }
            onerror = {
                it.preventDefault()
                it.stopPropagation()
                cont.resumeWithException(reinterpretException(this@result.error))
            }
        }
    }
}

private const val QUERY_DIRECTION_INCREASING = "next"
private const val QUERY_DIRECTION_DECREASING = "prev"
private const val OBJECT_FIELD_NAME = "c"

internal open class IndexedDBKeyCursor<K: Any, I: Any, C: IDBCursor>(
    protected val cursor: C,
    private val keySerializer: KeySerializer<K>,
    private val indexSerializer: KeySerializer<I>?,
): KeyCursor<K, I> {
    override val key: K
        get() {
            return deserializeKeyOrIndex(keySerializer, cursor.primaryKey) ?: throw RuntimeException("Unexpected null key: ${cursor.primaryKey}")
        }
    override val indexKey: I
        get() {
            val ser = indexSerializer ?: throw IllegalStateException("Not iterating through index")
            return deserializeKeyOrIndex(ser, cursor.key) ?: throw RuntimeException("Unexpected null indexKey: ${cursor.key}")
        }
}

internal class IndexedDBCursor<K: Any, I: Any, V: Any>(
    cursor: IDBCursorWithValue,
    private val table: Table<K, V>,
    index: Index<K, I, V>?
) : IndexedDBKeyCursor<K, I, IDBCursorWithValue>(cursor, table.keySerializer, index?.indexSerializer), MutableCursor<K, I, V> {

    override val value: V
        get() = deserializeValue(table, cursor.value) ?: throw RuntimeException("Unexpected null value: ${cursor.value}")

    override suspend fun update(newValue: V) {
        reinterpretExceptions {
            cursor.update(table.buildValue(key, newValue))
                .result()
        }
    }

    override suspend fun delete() {
        reinterpretExceptions {
            cursor.delete()
                .result()
        }
    }
}

internal open class IndexedDBTransaction(val transaction: IDBTransaction) : Transaction {

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.count(): Int {
        return reinterpretExceptions {
            queryable().count(range)
                .result()
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getFirst(): V? {
        return reinterpretExceptions {
            if (increasing && range !== undefined) {
                deserializeValue(table, queryable().get(range)
                    .result()
                    .unsafeCast<dynamic>())
            } else {
                deserializeValue(table, queryable().openCursor(range, if (increasing) QUERY_DIRECTION_INCREASING else QUERY_DIRECTION_DECREASING)
                    .result()
                    .unsafeCast<IDBCursorWithValue?>()?.value)
            }
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getFirstKey(): K? {
        return reinterpretExceptions {
            if (increasing && range !== undefined) {
                deserializeKeyOrIndex(table.keySerializer, queryable().getKey(range)
                    .result()
                    .unsafeCast<dynamic>())
            } else {
                deserializeKeyOrIndex(table.keySerializer, queryable().openKeyCursor(range, if (increasing) QUERY_DIRECTION_INCREASING else QUERY_DIRECTION_DECREASING)
                    .result()
                    .unsafeCast<IDBCursor?>()?.primaryKey)
            }
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getAll(limit: Int): List<V> {
        reinterpretExceptions {
            val noLimit = limit <= 0
            val store = queryable()
            if (increasing || noLimit) {
                val request = if (noLimit) store.getAll(range) else store.getAll(range, limit)
                val result = request
                    .result()
                    .unsafeCast<Array<dynamic>>()
                if (result.isEmpty()) return emptyList()
                val deserializedResult = ArrayList<V>(result.size)
                for (serialized in result) {
                    deserializedResult.add(deserializeValue(table, serialized)!!)
                }
                if (!increasing) {
                    deserializedResult.reverse()
                }
                return deserializedResult
            } else {
                // Decreasing and limited, must implement with a cursor
                val cursorRequest = store.openCursor(range, QUERY_DIRECTION_DECREASING)
                val cursor = cursorRequest
                    .result()
                    ?: return emptyList()
                val result = ArrayList<V>()
                result.add(deserializeValue(table, cursor.value)!!)
                while (result.size < limit) {
                    cursor.`continue`()
                    val newCursor = cursorRequest
                        .result()
                        ?: break
                    if (newCursor !== cursor) throw AssertionError("newCursor != cursor")
                    result.add(deserializeValue(table, cursor.value)!!)
                }
                return result
            }
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getAllKeys(limit: Int): List<K> {
        reinterpretExceptions {
            val noLimit = limit <= 0
            val store = queryable()
            if (increasing || noLimit) {
                val request = if (noLimit) store.getAllKeys(range) else store.getAllKeys(range, limit)
                val result = request
                    .result()
                    .unsafeCast<Array<dynamic>>()
                if (result.isEmpty()) return emptyList()
                val deserializedResult = ArrayList<K>(result.size)
                for (serialized in result) {
                    deserializedResult.add(deserializeKeyOrIndex(table.keySerializer, serialized)!!)
                }
                if (!increasing) {
                    deserializedResult.reverse()
                }
                return deserializedResult
            } else {
                // Decreasing and limited, must implement with a cursor
                val cursorRequest = store.openKeyCursor(range, QUERY_DIRECTION_DECREASING)
                val cursor = cursorRequest
                    .result()
                    ?: return emptyList()
                val result = ArrayList<K>()
                result.add(deserializeKeyOrIndex(table.keySerializer, cursor.primaryKey)!!)
                while (result.size < limit) {
                    cursor.`continue`()
                    val newCursor = cursorRequest
                        .result()
                        ?: break
                    if (newCursor !== cursor) throw AssertionError("newCursor != cursor")
                    result.add(deserializeKeyOrIndex(table.keySerializer, cursor.primaryKey)!!)
                }
                return result
            }
        }
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterateKeys(): Flow<KeyCursor<K, I>> {
        return flow {
            reinterpretExceptions {
                val cursorRequest = queryable().openKeyCursor(range, if (increasing) QUERY_DIRECTION_INCREASING else QUERY_DIRECTION_DECREASING)
                val cursor = cursorRequest.result() ?: return@flow // Nothing in it
                val cur = IndexedDBKeyCursor(cursor, table.keySerializer, index?.indexSerializer)
                emit(cur)
                while (true) {
                    cursor.`continue`()
                    val newCursor = cursorRequest.result() ?: return@flow
                    if (newCursor !== cursor) throw AssertionError("newCursor != cursor")
                    emit(cur)
                }
            }
        }
    }

    internal fun <K:Any,I:Any,V:Any> Query<K, I, V>.createIterateFlow(): Flow<IndexedDBCursor<K, I, V>> {
        return flow {
            reinterpretExceptions {
                val cursorRequest = queryable().openCursor(range, if (increasing) QUERY_DIRECTION_INCREASING else QUERY_DIRECTION_DECREASING)
                val cursor = cursorRequest.result() ?: return@flow // Nothing in it
                val cur = IndexedDBCursor(cursor, table, index)
                emit(cur)
                // The flow won't get here if the collection is cancelled
                while (true) {
                    cursor.`continue`()
                    val newCursor = cursorRequest.result() ?: return@flow
                    if (newCursor !== cursor) throw AssertionError("newCursor != cursor")
                    emit(cur)
                }
                // No cursor close yet: https://github.com/w3c/IndexedDB/issues/185
            }
        }
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterate(): Flow<Cursor<K, I, V>> {
        return createIterateFlow()
    }
}

internal class IndexedDBWriteTransaction(transaction: IDBTransaction) : IndexedDBTransaction(transaction), WriteTransaction {
    override suspend fun Query<*, Nothing, *>.delete() {
        reinterpretExceptions {
            val store = transaction.objectStore(table.name)
            val request = if (range == undefined) {
                store.clear()
            } else {
                store.delete(range)
            }
            request.result()
        }
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.add(key: K, value: V) {
        reinterpretExceptions {
            transaction.objectStore(name).add(buildValue(key, value), buildKey(key)).result()
        }
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.set(key: K, value: V) {
        reinterpretExceptions {
            transaction.objectStore(name).put(buildValue(key, value), buildKey(key)).result()
        }
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.writeIterate(): Flow<MutableCursor<K, I, V>> {
        return createIterateFlow()
    }
}

actual fun <K:Any, V:Any> Table<K, V>.queryAll(increasing: Boolean): Query<K, Nothing, V> = Query(this, null, undefined, increasing)
actual fun <K:Any, V:Any> Table<K, V>.queryOne(value: K): Query<K, Nothing, V> = Query(this, null, IDBKeyRange.only(serialize(keySerializer, value)), true)

private fun <K:Any> range(keySerializer: KeySerializer<K>, min: K?, max: K?, openMin: Boolean, openMax: Boolean): IDBKeyRange? {
    return if (min == null) {
        if (max == null) {
            null
        } else {
            val maxS = serialize(keySerializer, max)
            IDBKeyRange.upperBound(maxS, openMin)
        }
    } else if (max == null) {
        val minS = serialize(keySerializer, min)
        IDBKeyRange.lowerBound(minS, openMin)
    } else {
        val minS = serialize(keySerializer, min)
        if (min === max) {
            IDBKeyRange.bound(minS, minS, openMin, openMax)
        } else {
            val maxS = serialize(keySerializer, max)
            IDBKeyRange.bound(minS, maxS, openMin, openMax)
        }
    }
}
actual fun <K: Any, V: Any> Table<K, V>.query(min: K?, max: K?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, Nothing, V> = Query(this, null, range(keySerializer, min, max, openMin, openMax), increasing)
actual fun <K: Any, I:Any, V: Any> Index<K, I, V>.query(min: I?, max: I?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, I, V> = Query(table, this@query, range(indexSerializer, min, max, openMin, openMax), increasing)

actual class Query<K: Any, I: Any, V: Any>(
    internal val table: Table<K, V>,
    internal val index: Index<K, I, V>?,
    internal val range: dynamic,
    internal val increasing: Boolean,
    ) {

    internal fun IndexedDBTransaction.queryable(): IDBQueryable {
        val store = try {
            transaction.objectStore(table.name)
        } catch (e: dynamic) {
            throw IllegalArgumentException("Table $table not found (available: ${JSON.stringify(transaction.db.objectStoreNames)})", e.unsafeCast<Throwable?>())
        }
        return if (index != null) {
            try {
                store.index(index.canonicalName)
            } catch (e: dynamic) {
                throw IllegalArgumentException("Index $index not found (available: ${JSON.stringify(store.asDynamic().indexNames)})", e.unsafeCast<Throwable?>())
            }
        } else store
    }
}

internal fun <V:Any> deserializeValue(table: Table<*, V>, valueObject: dynamic): V? {
    if (valueObject == null) {
        return null
    }
    val serialized = if (table.indices.isEmpty()) {
        valueObject
    } else {
        val type = jsTypeOf(valueObject)
        if (type != "object") throw RuntimeException("Value is not object but $type: $valueObject")
        valueObject[OBJECT_FIELD_NAME]
    }
    val data = arrayBufferToByteArray(serialized)
    return deserialize(table.valueSerializer, data)
}

internal fun <KI: Any> deserializeKeyOrIndex(serializer: KeySerializer<KI>, keyIndexObject: dynamic): KI? {
    if (keyIndexObject == null) return null
    val data = arrayBufferToByteArray(keyIndexObject)
    return deserialize(serializer, data)
}

internal fun <K:Any> Table<K, *>.buildKey(key: K): dynamic {
    return serialize(keySerializer, key)
}

internal fun <K: Any, I: Any, V: Any> Index<K, I, V>.buildIndex(key: K, value: V): dynamic {
    val index = indexExtractor(key, value)
    return serialize(indexSerializer, index)
}

internal fun <K:Any, V:Any> Table<K, V>.buildValue(key:K, value: V): dynamic {
    val serialized = serialize(valueSerializer, value)
    return if (indices.isEmpty()) {
        serialized
    } else {
        val withIndices = js("{}")
        withIndices[OBJECT_FIELD_NAME] = serialized
        for (index in indices) {
            withIndices[index.fieldName] = index.buildIndex(key, value)
        }
        withIndices
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun openIndexedDBUD(config: BackendDatabaseConfig): OpenDBResult {
    val factory = window.indexedDB ?: return OpenDBResult.StorageNotSupported("IndexedDB not supported")
    val validSchema = config.schema
    val schema = validSchema.last()

    // https://w3c.github.io/IndexedDB/#opening
    val request = try {
        reinterpretExceptions {
            factory.open(config.name, schema.version)
        }
    } catch (e: Throwable) {
        if (e is KDOMException) {
            if (e.name == "SecurityError") {
                return OpenDBResult.StorageNotSupported("IndexedDB.open SecurityError")
            }
        }
        return OpenDBResult.Failure(e)
    }

    return coroutineScope {
        suspendCancellableCoroutine { cont ->
            request.onerror = error@{
                val e = reinterpretException(request.error)
                if (e is KDOMException) {
                    when (e.name) {
                        "QuotaExceededError" -> {
                            cont.resume(OpenDBResult.OutOfMemory)
                            return@error
                        }
                        "VersionError" -> {
                            cont.resume(OpenDBResult.NewerVersionExists)
                            return@error
                        }
                        "UnknownError" -> {
                            cont.resume(OpenDBResult.UnknownError)
                            return@error
                        }
                        "AbortError" -> {
                            cont.resumeWithException(CancellationException("Opening was aborted (${e.message})"))
                            return@error
                        }
                        "InvalidStateError" -> {
                            cont.resume(OpenDBResult.StorageNotSupported("IndexedDB InvalidStateError"))
                            return@error
                        }
                    }
                }
                cont.resumeWithException(RuntimeException("Unknown failure to open DB", e))
            }
            val postMigrationCallbacks = ArrayList<() -> Unit>()
            request.onsuccess = {
                val db = request.result
                val idb = IndexedDBUniversalDatabase(db)
                db.addEventListener("versionchange", {
                    config.onVersionChangeRequest?.invoke(idb)
                })
                db.addEventListener("close", {
                    idb.closed = true
                    config.onForceClose?.invoke()
                })
                db.addEventListener("error", {
                    console.error("!!!! Database got unhandled error event !!!!")
                    idb.closed = true
                    config.onForceClose?.invoke()
                    try {
                        db.close()
                    } catch (ignored: dynamic) {}
                })
                db.addEventListener("abort", {
                    console.error("!!!! Database got unhandled abort event !!!!")
                    idb.closed = true
                    config.onForceClose?.invoke()
                    try {
                        db.close()
                    } catch (ignored: dynamic) {}
                })
                var callbackError: Throwable? = null
                for (callback in postMigrationCallbacks) {
                    try {
                        callback()
                    } catch (t: Throwable) {
                        if (callbackError == null) {
                            callbackError = t
                        } else {
                            callbackError.addSuppressed(t)
                        }
                    }
                }
                if (callbackError == null) {
                    cont.resume(OpenDBResult.Success(idb)) {
                        db.close()
                    }
                } else {
                    db.close()
                    cont.resumeWithException(callbackError)
                }
            }
            request.onblocked = {
                config.onOpeningBlocked?.invoke()
            }
            request.onupgradeneeded = { versionChangeEvent ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    val oldV = versionChangeEvent.oldVersion
                    val database = request.result
                    runTransaction(IndexedDBWriteTransaction(request.transaction!!), true) {
                        val migrateFromIndex = validSchema.indexOfFirst { it.version == oldV }

                        if (migrateFromIndex < 0) {
                            // Create new version directly
                            if (oldV != 0) {
                                // Delete whole database first
                                for (oldStore in database.objectStoreNames.toList()) {
                                    database.deleteObjectStore(oldStore)
                                }
                            }

                            for (table in schema.tables) {
                                val store = database.createObjectStore(table.name)
                                for (index in table.indices) {
                                    val options = js("{}")
                                    options.unique = index.unique
                                    store.createIndex(index.canonicalName, index.fieldName, options)
                                }
                            }
                            if (schema.createdNew != null) {
                                schema.createdNew.invoke(this@runTransaction)
                            }
                            if (schema.afterSuccessfulCreationOrMigration != null) {
                                postMigrationCallbacks.add(schema.afterSuccessfulCreationOrMigration)
                            }
                        } else {
                            for (nextSchemaIndex in migrateFromIndex + 1 until validSchema.size) {
                                val currentSchema = validSchema[nextSchemaIndex - 1]
                                val nextSchema = validSchema[nextSchemaIndex]

                                for (table in nextSchema.tables) {
                                    if (table !in currentSchema.tables) {
                                        val store = database.createObjectStore(table.name)
                                        for (index in table.indices) {
                                            val options = js("{}")
                                            options.unique = index.unique
                                            store.createIndex(index.canonicalName, index.fieldName, options)
                                        }
                                    }
                                }
                                if (nextSchema.migrateFromPrevious != null) {
                                    nextSchema.migrateFromPrevious.invoke(this@runTransaction)
                                }
                                if (schema.afterSuccessfulCreationOrMigration != null) {
                                    postMigrationCallbacks.add(schema.afterSuccessfulCreationOrMigration)
                                }
                                for (table in currentSchema.tables) {
                                    if (table !in nextSchema.tables) {
                                        database.deleteObjectStore(table.name)
                                    }
                                }
                            }
                        }
                    }.getOrThrow()// Propagate into the parent scope
                }
            }

            // There is nothing to call on the request on cancellation,
            // but the handlers are set up to work in that case
        }
    }
}

internal suspend fun deleteIndexedDBUD(config: BackendDatabaseConfig) {
    reinterpretExceptions {
        val factory = window.indexedDB ?: return
        factory.deleteDatabase(config.name).result()
    }
}

