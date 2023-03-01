package com.darkyen.database

import com.darkyen.cbor.CborSerializers
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.coroutineScope

class DatabaseObserverTest: FunSpec({

    val TableThing1 = Table("Thing1", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    val TableThing2 = Table("Thing2", LongKeySerializer, CborSerializers.StringSerializer, emptyList())
    val schema = Schema(1, listOf(TableThing1, TableThing2))

    databaseTest("Observer", schema) { config ->
        withDatabase(config) { db ->
            coroutineScope {
                val observe12 = db.observeDatabaseWrites(this, TableThing1, TableThing2)
                val observe1 = db.observeDatabaseWrites(this, TableThing1)
                val observe2 = db.observeDatabaseWrites(this, TableThing2)

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

})