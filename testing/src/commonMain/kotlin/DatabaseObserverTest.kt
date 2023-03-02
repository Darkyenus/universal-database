package com.darkyen.database

import com.darkyen.cbor.CborSerializers
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class DatabaseObserverTest: TestContainer({

    val TableThing1 = Table("Thing1", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    val TableThing2 = Table("Thing2", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    val schema = Schema(1, listOf(TableThing1, TableThing2))

    databaseTest("scope observer", schema) { config ->
        withDatabase(config) { db ->
            val scope = CoroutineScope(Job())

            val observe12 = db.observeDatabaseWrites(scope, TableThing1, TableThing2)
            val observe1 = db.observeDatabaseWrites(scope, TableThing1)
            val observe2 = db.observeDatabaseWrites(scope, TableThing2)

            observe12.checkWrite().shouldBeFalse()
            observe1.checkWrite().shouldBeFalse()
            observe2.checkWrite().shouldBeFalse()

            db.writeTransaction(TableThing1, TableThing2) {}

            observe12.checkWrite().shouldBeTrue()
            observe1.checkWrite().shouldBeTrue()
            observe2.checkWrite().shouldBeTrue()

            observe12.checkWrite().shouldBeFalse()
            observe1.checkWrite().shouldBeFalse()
            observe2.checkWrite().shouldBeFalse()

            db.writeTransaction(TableThing1) {}

            observe12.checkWrite().shouldBeTrue()
            observe1.checkWrite().shouldBeTrue()
            observe2.checkWrite().shouldBeFalse()

            observe12.checkWrite().shouldBeFalse()
            observe1.checkWrite().shouldBeFalse()
            observe2.checkWrite().shouldBeFalse()

            scope.cancel("Done")
        }
    }

    databaseTest("nested observer", schema) { config ->
        withDatabase(config) { db ->
            db.observeDatabaseWrites(TableThing1, TableThing2) {
                val observe12 = this
                db.observeDatabaseWrites(TableThing1) {
                    val observe1 = this
                    db.observeDatabaseWrites(TableThing2) {
                        val observe2 = this

                        observe12.checkWrite().shouldBeFalse()
                        observe1.checkWrite().shouldBeFalse()
                        observe2.checkWrite().shouldBeFalse()

                        db.writeTransaction(TableThing1, TableThing2) {}

                        observe12.checkWrite().shouldBeTrue()
                        observe1.checkWrite().shouldBeTrue()
                        observe2.checkWrite().shouldBeTrue()

                        observe12.checkWrite().shouldBeFalse()
                        observe1.checkWrite().shouldBeFalse()
                        observe2.checkWrite().shouldBeFalse()

                        db.writeTransaction(TableThing1) {}

                        observe12.checkWrite().shouldBeTrue()
                        observe1.checkWrite().shouldBeTrue()
                        observe2.checkWrite().shouldBeFalse()

                        observe12.checkWrite().shouldBeFalse()
                        observe1.checkWrite().shouldBeFalse()
                        observe2.checkWrite().shouldBeFalse()
                    }
                }
            }
        }
    }

})