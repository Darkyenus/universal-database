package com.darkyen.ud

import com.darkyen.ucbor.CborSerializers
import com.juul.indexeddb.external.indexedDB
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.browser.window

val databaseName = "BirdDB"

@Suppress("unused")
class DatabaseMigrationTest : FunSpec({

    afterEach {
        window.indexedDB!!.deleteDatabase(databaseName).result()
    }

    test("database opening simple") {
        val config = BackendDatabaseConfig(
            databaseName, Schema(
                1, listOf(
                    Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
                )
            )
        )
        withDatabase(config) {
            // Good
        }
        deleteUniversalDatabase(config)
    }

    val simpleBirds = listOf(
        156L to "sparrow",
        3265L to "owl",
        13L to "eagle",
    )

    test("database using") {
        val birdsTable = Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
        val config = BackendDatabaseConfig(
            databaseName, Schema(
                1, listOf(
                    birdsTable
                )
            )
        )

        for (bird in simpleBirds) {
            withDatabase(config) { db ->
                db.writeTransaction(birdsTable) {
                    birdsTable.add(bird.first, bird.second)
                }
            }
        }

        withDatabase(config) { db ->
            val allBirds = db.transaction(birdsTable) {
                birdsTable.queryAll().getAll()
            }.shouldBeSuccess()
            allBirds.shouldContainInOrder(simpleBirds.sortedBy { it.first }.map { it.second })
        }

        deleteUniversalDatabase(config)
    }

    test("database opening migration") {
        val birds1 = Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
        val birds2 = Table("FunkyBirds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
        val schema1 = Schema(1, listOf(birds1), createdNew = {
            for (bird in simpleBirds) {
                birds1.add(bird.first, bird.second)
            }
        })
        val schema2 = Schema(2, listOf(birds2), migrateFromPrevious = {
            birds1.queryAll().iterate().collect { cursor ->
                birds2.add(cursor.key, cursor.value.uppercase())
            }
        })
        withDatabase(BackendDatabaseConfig(databaseName, schema1)) { db ->
            val allBirds = db.transaction(birds1) {
                birds1.queryAll().getAll()
            }.shouldBeSuccess()
            allBirds.shouldContainInOrder(simpleBirds.sortedBy { it.first }.map { it.second })
        }

        val config2 = BackendDatabaseConfig(databaseName, schema1, schema2)
        withDatabase(config2) { db ->
            val allBirds = db.transaction(birds2) {
                birds2.queryAll().getAll()
            }.shouldBeSuccess()
            allBirds.shouldContainInOrder(simpleBirds.sortedBy { it.first }.map { it.second.uppercase() })

            shouldThrowAny {
                db.transaction(birds1) {}
            }
        }
        deleteUniversalDatabase(config2)
    }

    test("closing db stops transactions") {
        val birdsTable = Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
        val schema = Schema(1, listOf(birdsTable))
        val db = withDatabase(BackendDatabaseConfig(databaseName, schema)) {
            it
        }
        shouldThrowAny {
            db.transaction(birdsTable) {}
        }
    }
})

suspend inline fun <T> withDatabase(config: BackendDatabaseConfig, block: (Database) -> T):T {
    val result = openUniversalDatabase(config)
    result.shouldBeInstanceOf<OpenDBResult.Success>()
    val db = result.db
    val res = block(db)
    db.close()
    return res
}