package com.darkyen.ud

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File


actual fun <K : Any, V : Any> Table<K, V>.queryAll(): Query<K, Nothing, V> {
    TODO("Not yet implemented")
}
actual fun <K : Any, V : Any> Table<K, V>.queryOne(value: K): Query<K, Nothing, V> {
    TODO("Not yet implemented")
}
actual fun <K : Any, V : Any> Table<K, V>.query(min: K?, max: K?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, Nothing, V> {
    TODO("Not yet implemented")
}
actual fun <K: Any, I:Any, V: Any> Index<K, I, V>.query(min: I?, max: I?, openMin: Boolean, openMax: Boolean, increasing: Boolean): Query<K, I, V> {
    TODO("Not yet implemented")
}
/** Describes a set of objects in a database. */
actual class Query<Key : Any, Index : Any, Value : Any>

actual class BackendDatabaseConfig(
    name: String,
    vararg schema: Schema,
    val context: Context
): BaseDatabaseConfig(name, *schema)

actual suspend fun openUniversalDatabase(config: BackendDatabaseConfig): OpenDBResult {

    object : SQLiteOpenHelper(config.context, config.name, null, config.schema.last().version) {
        override fun onCreate(db: SQLiteDatabase?) {
            TODO("Not yet implemented")
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            TODO("Not yet implemented")
        }

        override fun onOpen(db: SQLiteDatabase?) {
            super.onOpen(db)
        }
    }
    TODO("Not yet implemented")
}

actual suspend fun deleteUniversalDatabase(config: BackendDatabaseConfig) {
    val filePath: File = config.context.getDatabasePath(config.name)
    if (!SQLiteDatabase.deleteDatabase(filePath) && filePath.exists()) {
        throw RuntimeException("Database ${config.name} still exists")
    }
}