package com.darkyen.database

import com.darkyen.cbor.CborSerializers
import com.darkyen.database.*
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess


@Suppress("unused")
class DatabaseMigrationTest : TestContainer({

    databaseTest("database opening simple", Schema(
        1, listOf(
            Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
        )
    )
    ) { config ->
        withDatabase(config) {
            // Good
        }
    }

    val simpleBirds = listOf(
        156L to "sparrow",
        3265L to "owl",
        13L to "eagle",
    )

    val birdsTable = Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    databaseTest("database using", Schema(
        1, listOf(
            birdsTable
        )
    )
    ) { config ->

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
    }

    val birds1 = Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    val birds2 = Table("FunkyBirds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    databaseTest("database opening migration", listOf(
        Schema(1, listOf(birds1), createdNew = {
            for (bird in simpleBirds) {
                birds1.add(bird.first, bird.second)
            }
        }),
        Schema(2, listOf(birds2), migrateFromPrevious = {
            birds1.queryAll().iterate().collect { cursor ->
                birds2.add(cursor.key, cursor.value.uppercase())
            }
        })
    )) { (config1, config2) ->
        withDatabase(config1) { db ->
            val allBirds = db.transaction(birds1) {
                birds1.queryAll().getAll()
            }.shouldBeSuccess()
            allBirds.shouldContainInOrder(simpleBirds.sortedBy { it.first }.map { it.second })
        }

        withDatabase(config2) { db ->
            val allBirds = db.transaction(birds2) {
                birds2.queryAll().getAll()
            }.shouldBeSuccess()
            allBirds.shouldContainInOrder(simpleBirds.sortedBy { it.first }.map { it.second.uppercase() })

            shouldThrowAny {
                db.transaction(birds1) {}
            }
        }
    }

    val closingBirdsTable = Table("birds", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    databaseTest("closing db stops transactions", Schema(1, listOf(closingBirdsTable))) { config ->
        val db = withDatabase(config) {
            it
        }
        db.transaction(closingBirdsTable) {}.shouldBeFailure<IllegalStateException>()
    }
})

