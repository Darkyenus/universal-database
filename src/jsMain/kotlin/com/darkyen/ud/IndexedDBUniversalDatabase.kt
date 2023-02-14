package com.darkyen.ud

import com.darkyen.ucbor.ByteData
import com.darkyen.ucbor.CborRead
import com.darkyen.ucbor.CborSerializer
import com.darkyen.ucbor.CborWrite
import com.juul.indexeddb.*
import com.juul.indexeddb.external.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class IndexedDBUniversalDatabase(
    private val db: IDBDatabase
) : Database {

    internal var closed = false

    override suspend fun <R> transaction(vararg usedTables: Table<*, *>, block: suspend Transaction.() -> R): Result<R> {
        return withContext(Dispatchers.Unconfined) {
            val tables = Array(usedTables.size) { usedTables[it].name }
            val trans = db.transaction(tables, "readonly")
            val transaction = IndexedDBTransaction(trans)
            val result = transaction.block()

            val event = trans.nextEvent("complete", "abort", "error")
            when (event.type) {
                "complete" -> {}
                "abort", "error" -> {
                    return@withContext Result.failure(wrapException(trans.error))
                }
            }

            Result.success(result)
        }
    }

    override suspend fun <R> writeTransaction(vararg usedTables: Table<*, *>, block: suspend WriteTransaction.() -> R): Result<R> {
        return withContext(Dispatchers.Unconfined) {
            val tables = Array(usedTables.size) { usedTables[it].name }
            val trans = db.transaction(tables, "readwrite")
            val transaction = IndexedDBWriteTransaction(trans)
            val result = transaction.block()

            val event = trans.nextEvent("complete", "abort", "error")
            when (event.type) {
                "complete" -> {}
                "abort", "error" -> {
                    return@withContext Result.failure(wrapException(trans.error))
                }
            }

            Result.success(result)
        }
    }

    override fun close() {
        this.closed = true
        db.close()
    }
}

private suspend fun <T> IDBRequest<T>.result():T {
    return suspendCancellableCoroutine { cont ->
        onsuccess = {
            cont.resume(this@result.result)
        }
        onerror = {
            cont.resumeWithException(wrapException(this@result.error))
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
        cursor.update(table.buildValue(key, newValue)).result()
    }

    override suspend fun delete() {
        cursor.delete().result()
    }
}

internal open class IndexedDBTransaction(val transaction: IDBTransaction) : Transaction {

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.count(): Int {
        return queryable().count().result()
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getFirst(): V? {
        return if (increasing) {
            deserializeValue(table, queryable().get(range).result().unsafeCast<dynamic>())
        } else {
            deserializeValue(table, queryable().openCursor(range, QUERY_DIRECTION_DECREASING).result().unsafeCast<IDBCursorWithValue>().value)
        }
    }

    override suspend fun <K : Any, I : Any, V : Any> Query<K, I, V>.getAll(limit: Int): List<V> {
        val store = queryable()
        val request = if (limit <= 0) store.getAll() else store.getAll(limit)
        val result = request.result().unsafeCast<Array<dynamic>>()
        if (result.isEmpty()) return emptyList()
        val deserializedResult = ArrayList<V>(result.size)
        for (serialized in result) {
            deserializedResult.add(deserializeValue(table, serialized)!!)
        }
        if (!increasing) {
            deserializedResult.reverse()
        }
        return deserializedResult
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterateKeys(): Flow<KeyCursor<K, I>> {
        return flow {
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

    internal fun <K:Any,I:Any,V:Any> Query<K, I, V>.createIterateFlow(): Flow<IndexedDBCursor<K, I, V>> {
        return flow {
            val cursorRequest = queryable().openCursor(range, if (increasing) QUERY_DIRECTION_INCREASING else QUERY_DIRECTION_DECREASING)
            val cursor = cursorRequest.result() ?: return@flow // Nothing in it
            val cur = IndexedDBCursor(cursor, table, index)
            emit(cur)
            //TODO Make sure that if the iteration is cancelled, we won't get here
            while (true) {
                cursor.`continue`()
                val newCursor = cursorRequest.result() ?: return@flow
                if (newCursor !== cursor) throw AssertionError("newCursor != cursor")
                emit(cur)
            }
        }
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.iterate(): Flow<Cursor<K, I, V>> {
        return createIterateFlow()
    }
}

internal class IndexedDBWriteTransaction(transaction: IDBTransaction) : IndexedDBTransaction(transaction), WriteTransaction {
    override suspend fun Query<*, Nothing, *>.delete() {
        transaction.objectStore(table.name).delete(range).result()
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.add(key: K, value: V) {
        transaction.objectStore(name).add(buildValue(key, value), buildKey(key)).result()
    }

    override suspend fun <K : Any, V : Any> Table<K, V>.set(key: K, value: V) {
        transaction.objectStore(name).put(buildValue(key, value), buildKey(key)).result()
    }

    override fun <K : Any, I : Any, V : Any> Query<K, I, V>.writeIterate(): Flow<MutableCursor<K, I, V>> {
        return createIterateFlow()
    }
}

actual fun <K:Any, V:Any> Table<K, V>.queryAll(): Query<K, Nothing, V> = Query(this, null, undefined, true)
actual fun <K:Any, V:Any> Table<K, V>.queryOne(value: K): Query<K, Nothing, V> = Query(this, null, IDBKeyRange.only(serialize(keySerializer, value)), true)
actual fun <K: Any, V: Any> Table<K, V>.query(min: K?, max: K?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, Nothing, V> = Query(this, null, IDBKeyRange.bound(min?.let { serialize(keySerializer, it) } ?: undefined, max?.let { serialize(keySerializer, it) } ?: undefined, openMin, openMax), increasing)
actual fun <K: Any, I:Any, V: Any> Index<K, I, V>.query(min: I?, max: I?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, I, V> = Query(table, this@query, IDBKeyRange.bound(min?.let { serialize(indexSerializer, it) } ?: undefined, max?.let { serialize(indexSerializer, it) } ?: undefined, openMin, openMax), increasing)

actual class Query<K: Any, I: Any, V: Any>(
    internal val table: Table<K, V>,
    internal val index: Index<K, I, V>?,
    internal val range: dynamic,
    internal val increasing: Boolean,
    ) {

    internal fun IndexedDBTransaction.queryable(): IDBQueryable {
        val store = transaction.objectStore(table.name)
        return if (index != null) {
            store.index(index.name)
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

/** JavaScript is single-threaded, so this is fine.
 * Shared buffer used for de/serialization of all kinds of values. */
private val readByteData = ByteData()// Does not carry its own buffer
private val cborRead = CborRead(readByteData)
private val writeByteData = ByteData()// Does carry its own buffer
private val cborWrite = CborWrite(writeByteData)

private fun <T: Any> serialize(serializer: CborSerializer<T>, value: T): ByteArray {
    val byteData = writeByteData
    byteData.resetForWriting(true)
    val cborWrite = cborWrite
    cborWrite.reset()
    cborWrite.value(value, serializer)
    return byteData.toByteArray()
}
private fun <T: Any> serialize(serializer: KeySerializer<T>, value: T): ByteArray {
    val byteData = writeByteData
    byteData.resetForWriting(true)
    serializer.serialize(byteData, value)
    return byteData.toByteArray()
}
private fun <T: Any> deserialize(serializer: CborSerializer<T>, value: ByteArray): T {
    val byteData = readByteData
    byteData.resetForReading(value)
    val cborRead = cborRead
    cborRead.reset()
    return cborRead.value(serializer)
}
private fun <T: Any> deserialize(serializer: KeySerializer<T>, value: ByteArray): T {
    val byteData = readByteData
    byteData.resetForReading(value)
    return serializer.deserialize(byteData)
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
    val byteData = writeByteData
    byteData.resetForWriting(true)
    indexSerializer.serialize(byteData, index)
    return byteData.toByteArray()
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
    val factory = window.indexedDB ?: return OpenDBResult.StorageNotSupported
    val validSchema = config.schema
    val schema = validSchema.last()

    // https://w3c.github.io/IndexedDB/#opening
    val request = try {
        factory.open(config.name, schema.version)
    } catch (ed: dynamic) {
        val e = catchDOMException(ed)
        if (e.name == "SecurityError") {
            return OpenDBResult.StorageNotSupported
        }
        throw RuntimeException("Failed to open IndexedDB")
    }

    return withContext(Dispatchers.Unconfined) {
        suspendCancellableCoroutine { cont ->
            request.onerror = error@{
                val dom = asDOMException(request.error)
                if (dom != null) {
                    when (dom.name) {
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
                            cont.resumeWithException(CancellationException("Opening was aborted (${dom.message})"))
                            return@error
                        }
                    }
                    cont.resumeWithException(RuntimeException("Unknown error when opening DB (${dom.name}: ${dom.message})"))
                } else {
                    cont.resumeWithException(RuntimeException("Unknown failure to open DB", request.error))
                }
            }
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
                cont.resume(OpenDBResult.Success(idb)) {
                    db.close()
                }
            }
            request.onblocked = {
                config.onOpeningBlocked?.invoke()
            }
            request.onupgradeneeded = { versionChangeEvent ->
                launch {
                    val oldV = versionChangeEvent.oldVersion
                    val database = request.result
                    val trans = request.transaction!!
                    trans.onabort = {
                        val dom = asDOMException(trans.error)
                        if (dom != null) {
                            cont.resumeWithException(CancellationException("Database upgrade was aborted (${dom.name}: ${dom.message})"))
                        } else {
                            cont.resumeWithException(CancellationException("Database upgrade was aborted", trans.error))
                        }
                    }
                    trans.onerror = {
                        val dom = asDOMException(trans.error)
                        if (dom != null) {
                            cont.resumeWithException(CancellationException("Database upgrade failed (${dom.name}: ${dom.message})"))
                        } else {
                            cont.resumeWithException(CancellationException("Database upgrade failed", trans.error))
                        }
                    }
                    trans.oncomplete = {
                        //TODO LOG
                    }
                    val transaction = IndexedDBWriteTransaction(trans)

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
                                store.createIndex(index.canonicalName, index.fieldName, index.unique)
                            }
                        }
                        if (schema.createdNew != null) {
                            schema.createdNew.invoke(transaction)
                        }
                    } else {
                        for (nextSchemaIndex in migrateFromIndex + 1 until validSchema.size) {
                            val currentSchema = validSchema[nextSchemaIndex - 1]
                            val nextSchema = validSchema[nextSchemaIndex]

                            for (table in nextSchema.tables) {
                                if (table !in currentSchema.tables) {
                                    val store = database.createObjectStore(table.name)
                                    for (index in table.indices) {
                                        store.createIndex(index.canonicalName, index.fieldName, index.unique)
                                    }
                                }
                            }
                            if (nextSchema.migrateFromPrevious != null) {
                                nextSchema.migrateFromPrevious.invoke(transaction)
                            }
                            for (table in currentSchema.tables) {
                                if (table !in nextSchema.tables) {
                                    database.deleteObjectStore(table.name)
                                }
                            }
                        }
                    }
                }
            }

            // There is nothing to call on the request on cancellation,
            // but the handlers are set up to work in that case
        }
    }
}

internal suspend fun deleteIndexedDBUD(config: BackendDatabaseConfig) {
    val factory = window.indexedDB ?: return
    factory.deleteDatabase(config.name).result()
}

