package com.darkyen.database

import com.darkyen.ucbor.CborSerializers
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.mpp.timeInMillis
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise
import kotlin.random.Random

class DatabaseTest : FunSpec({

    val birdlifeIDIndex = Index<Long, Long, Bird>("BirdlifeID", { _, v -> v.birdlifeId }, UnsignedLongKeySerializer, true)
    val conservationIndex = Index<Long, ConservationStatus, Bird>("Conservation", { _, v -> v.conservationStatus }, ConservationStatus.KEY_SERIALIZER, false)
    val wingspanIndex = Index<Long, Float, Bird>("Wingspan", { _, v -> v.wingspanM }, FloatKeySerializer, false)
    // Table with indices
    val birdTable = Table("Birds", LongKeySerializer, BIRD_SERIALIZER, listOf(
        birdlifeIDIndex,
        conservationIndex,
        wingspanIndex,
    ))
    // Table without indices
    val sightingTable = Table("Sightings", LongKeySerializer, BIRD_SIGHTING_SERIALIZER, emptyList())

    val schema = Schema(1, listOf(birdTable, sightingTable))
    val config = BackendDatabaseConfig("Birds", schema)

    afterEach {
        deleteUniversalDatabase(config)
    }

    test("suspend functions are banned") {
        withDatabase(config) { db ->
            withClue("delay") {
                db.transaction(birdTable) {
                    birdTable.queryAll().count() shouldBe 0

                    delay(100)
                }.shouldBeFailure { err ->
                    err.shouldBeInstanceOf<IllegalStateException>()
                    err.shouldHaveMessage("Transaction completed before block, this indicates incorrect use of suspend functions")
                }
            }

            withClue("fetch") {
                db.transaction(birdTable) {
                    birdTable.queryAll().count() shouldBe 0

                    window.fetch("/the-url-does-not-matter-suspend-does").await()
                }.shouldBeFailure { err ->
                    err.shouldBeInstanceOf<IllegalStateException>()
                    err.shouldHaveMessage("Transaction completed before block, this indicates incorrect use of suspend functions")
                }
            }

            withClue("redispatch") {
                db.transaction(birdTable) {
                    birdTable.queryAll().count() shouldBe 0

                    coroutineScope {
                        val jobs = List(1000) { async {
                            (0..1000).fold(0) { i, a -> i + a }
                        } }
                        withContext(Dispatchers.Default) {
                            withContext(Dispatchers.Main) {
                                jobs.awaitAll()
                            }
                        }
                    }

                    // Didn't reliably fail without this, not sure if that was supposed to happen or not
                    suspendCoroutine { cont ->
                        window.setTimeout({
                            cont.resume(Unit)
                        }, 10)
                    }
                }.shouldBeFailure { err ->
                    err.shouldBeInstanceOf<IllegalStateException>()
                    err.shouldHaveMessage("Transaction completed before block, this indicates incorrect use of suspend functions")
                }
            }
        }
    }

    test("single") {
        withDatabase(config) { db ->
            val bird = birds[3]
            val sighting = BirdSighting(bird.birdlifeId, timeInMillis(), 0.9)
            val BIRD_ID = 5L
            val SIGHTING_ID = 7L

            withClue("get from empty") {
                db.transaction(birdTable) {
                    for ((name, query) in birdTable.testQueries(BIRD_ID)) {
                        withClue(name) {
                            query.count() shouldBe 0
                            query.getFirst() shouldBe null
                            query.getFirstKey() shouldBe null
                            query.getAll() shouldBe listOf()
                            query.getAllKeys() shouldBe listOf()
                            query.getAll(10) shouldBe listOf()
                            query.getAllKeys(10) shouldBe listOf()
                            query.getAll(1) shouldBe listOf()
                            query.getAllKeys(1) shouldBe listOf()
                            query.getAll(0) shouldBe listOf()
                            query.getAllKeys(0) shouldBe listOf()
                            query.iterate().map { cursor -> cursor.key to cursor.value }.toList(ArrayList()) shouldBe listOf()
                            query.iterateKeys().map { cursor -> cursor.key }.toList(ArrayList()) shouldBe listOf()
                        }
                    }
                }.shouldBeSuccess()
                db.transaction(sightingTable) {
                    for ((name, query) in sightingTable.testQueries(SIGHTING_ID)) {
                        withClue(name) {
                            query.count() shouldBe 0
                            query.getFirst() shouldBe null
                            query.getFirstKey() shouldBe null
                            query.getAll() shouldBe listOf()
                            query.getAllKeys() shouldBe listOf()
                            query.getAll(10) shouldBe listOf()
                            query.getAllKeys(10) shouldBe listOf()
                            query.getAll(1) shouldBe listOf()
                            query.getAllKeys(1) shouldBe listOf()
                            query.getAll(0) shouldBe listOf()
                            query.getAllKeys(0) shouldBe listOf()
                            query.iterate().map { cursor -> cursor.key to cursor.value }.toList(ArrayList()) shouldBe listOf()
                            query.iterateKeys().map { cursor -> cursor.key }.toList(ArrayList()) shouldBe listOf()
                        }
                    }
                }.shouldBeSuccess()
            }

            withClue("insert") {
                db.writeTransaction(birdTable, sightingTable) {
                    birdTable.add(BIRD_ID, bird)
                    sightingTable.add(SIGHTING_ID, sighting)
                }.shouldBeSuccess()
            }
            withClue("update") {
                db.writeTransaction(birdTable, sightingTable) {
                    birdTable.set(BIRD_ID, bird)
                    sightingTable.set(SIGHTING_ID, sighting)
                }.shouldBeSuccess()
            }

            withClue("adding same id with add") {
                db.writeTransaction(birdTable) {
                    birdTable.add(BIRD_ID, bird)
                }.shouldBeFailure<ConstraintException>()
                db.writeTransaction(sightingTable) {
                    sightingTable.add(SIGHTING_ID, sighting)
                }.shouldBeFailure<ConstraintException>()
            }
            withClue("adding same unique index") {
                db.writeTransaction(birdTable) {
                    birdTable.add(6, bird)
                }.shouldBeFailure<ConstraintException>()
            }
            withClue("get it") {
                db.transaction(birdTable) {
                    for ((name, query) in birdTable.testQueries(BIRD_ID)) {
                        withClue("bird $name") {
                            withClue("count") { query.count() shouldBe 1 }
                            withClue("getFirst") { query.getFirst() shouldBe bird }
                            withClue("getFirstKey") { query.getFirstKey() shouldBe BIRD_ID }
                            withClue("getAll") { query.getAll() shouldBe listOf(bird) }
                            withClue("getAllKeys") { query.getAllKeys() shouldBe listOf(BIRD_ID) }
                            withClue("getAll(10)") { query.getAll(10) shouldBe listOf(bird) }
                            withClue("getAllKeys(10)") { query.getAllKeys(10) shouldBe listOf(BIRD_ID) }
                            withClue("getAll(1)") { query.getAll(1) shouldBe listOf(bird) }
                            withClue("getAllKeys(1)") { query.getAllKeys(1) shouldBe listOf(BIRD_ID) }
                            withClue("getAll(0)") { query.getAll(0) shouldBe listOf(bird) }
                            withClue("getAllKeys(0)") { query.getAllKeys(0) shouldBe listOf(BIRD_ID) }
                            withClue("getAll(-5)") { query.getAll(-5) shouldBe listOf(bird) }
                            withClue("getAllKeys(-5)") { query.getAllKeys(-5) shouldBe listOf(BIRD_ID) }
                            withClue("iterate") { query.iterate().map { cursor -> cursor.key to cursor.value }.toList(ArrayList()) shouldContainExactly listOf(BIRD_ID to bird) }
                            withClue("iterateKeys") { query.iterateKeys().map { cursor -> cursor.key }.toList(ArrayList()) shouldContainExactly listOf(BIRD_ID) }
                        }
                    }
                }.shouldBeSuccess()
                db.transaction(sightingTable) {
                    for ((name, query) in sightingTable.testQueries(SIGHTING_ID)) {

                        withClue("sighting $name") {
                            withClue("count") { query.count() shouldBe 1 }
                            withClue("getFirst") { query.getFirst() shouldBe sighting }
                            withClue("getFirstKey") { query.getFirstKey() shouldBe SIGHTING_ID }
                            withClue("getAll") { query.getAll() shouldBe listOf(sighting) }
                            withClue("getAllKeys") { query.getAllKeys() shouldBe listOf(SIGHTING_ID) }
                            withClue("getAll(10)") { query.getAll(10) shouldBe listOf(sighting) }
                            withClue("getAllKeys(10)") { query.getAllKeys(10) shouldBe listOf(SIGHTING_ID) }
                            withClue("getAll(1)") { query.getAll(1) shouldBe listOf(sighting) }
                            withClue("getAllKeys(1)") { query.getAllKeys(1) shouldBe listOf(SIGHTING_ID) }
                            withClue("getAll(0)") { query.getAll(0) shouldBe listOf(sighting) }
                            withClue("getAllKeys(0)") { query.getAllKeys(0) shouldBe listOf(SIGHTING_ID) }
                            withClue("getAll(-5)") { query.getAll(-5) shouldBe listOf(sighting) }
                            withClue("getAllKeys(-5)") { query.getAllKeys(-5) shouldBe listOf(SIGHTING_ID) }
                            withClue("iterate") { query.iterate().map { cursor -> cursor.key to cursor.value }.toList(ArrayList()) shouldContainExactly listOf(SIGHTING_ID to sighting) }
                            withClue("iterateKeys") { query.iterateKeys().map { cursor -> cursor.key }.toList(ArrayList()) shouldContainExactly listOf(SIGHTING_ID) }
                        }
                    }
                }.shouldBeSuccess()
            }
            withClue("get nothing") {
                db.transaction(birdTable) {
                    withClue("bird count") { birdTable.queryOne(88).count() shouldBe 0 }
                    withClue("bird getFirst") { birdTable.queryOne(88).getFirst() shouldBe null }
                    withClue("bird getFirstKey") { birdTable.queryOne(88).getFirstKey() shouldBe null }
                    withClue("bird getAll") { birdTable.queryOne(88).getAll() shouldBe listOf() }
                    withClue("bird getAll") { birdTable.query(BIRD_ID, 88, openMin = true).getAll() shouldBe listOf() }
                    withClue("bird getAll") { birdTable.query(BIRD_ID, 88, openMin = true, increasing = false).getAll() shouldBe listOf() }
                    withClue("bird getAllKeys") { birdTable.queryOne(88).getAllKeys() shouldBe listOf() }
                    withClue("bird getAllKeys") { birdTable.query(BIRD_ID, 88, openMin = true).getAllKeys() shouldBe listOf() }
                    withClue("bird getAllKeys") { birdTable.query(BIRD_ID, 88, openMin = true, increasing = false).getAllKeys() shouldBe listOf() }
                }.shouldBeSuccess()
                db.transaction(sightingTable) {
                    withClue("bird count") { sightingTable.queryOne(88).count() shouldBe 0 }
                    withClue("bird getFirst") { sightingTable.queryOne(88).getFirst() shouldBe null }
                    withClue("bird getFirstKey") { sightingTable.queryOne(88).getFirstKey() shouldBe null }
                    withClue("bird getAll") { sightingTable.queryOne(88).getAll() shouldBe listOf() }
                    withClue("bird getAll") { sightingTable.query(SIGHTING_ID, 88, openMin = true).getAll() shouldBe listOf() }
                    withClue("bird getAll") { sightingTable.query(SIGHTING_ID, 88, openMin = true, increasing = false).getAll() shouldBe listOf() }
                    withClue("bird getAllKeys") { sightingTable.queryOne(88).getAllKeys() shouldBe listOf() }
                    withClue("bird getAllKeys") { sightingTable.query(SIGHTING_ID, 88, openMin = true).getAllKeys() shouldBe listOf() }
                    withClue("bird getAllKeys") { sightingTable.query(SIGHTING_ID, 88, openMin = true, increasing = false).getAllKeys() shouldBe listOf() }
                }.shouldBeSuccess()
            }
        }
    }

    test("all") {
        withDatabase(config) { db ->
            withClue("insert all") {
                db.writeTransaction(birdTable, sightingTable) {
                    for ((i, bird) in birds.withIndex()) {
                        withClue({ "$i: $bird" }) {
                            birdTable.add(i.toLong(), bird)
                        }
                    }
                    for ((i, sighting) in sightings.withIndex()) {
                        withClue({ "$i: $sighting" }) {
                            sightingTable.add(i.toLong(), sighting)
                        }
                    }
                }.shouldBeSuccess()
            }

            // Get all
            withClue("get all birds") {
                db.transaction(birdTable) {
                    birdTable.queryAll().getAll() shouldContainExactly birds
                    birdTable.queryAll(increasing = false).getAll() shouldContainExactly birds.asReversed()
                    val longIndices = birds.indices.map { it.toLong() }
                    birdTable.queryAll().getAllKeys() shouldContainExactly longIndices
                    birdTable.queryAll(increasing = false).getAllKeys() shouldContainExactly longIndices.asReversed()
                }.shouldBeSuccess()
            }
            withClue("get all sightings") {
                db.transaction(sightingTable) {
                    sightingTable.queryAll().getAll() shouldContainExactly sightings
                    sightingTable.queryAll(increasing = false).getAll() shouldContainExactly sightings.asReversed()
                    val longIndices = sightings.indices.map { it.toLong() }
                    sightingTable.queryAll().getAllKeys() shouldContainExactly longIndices
                    sightingTable.queryAll(increasing = false).getAllKeys() shouldContainExactly longIndices.asReversed()
                }.shouldBeSuccess()
            }

            // Random access
            withClue("random access birds") {
                db.transaction(birdTable) {
                    for (i in birds.indices.shuffled()) {
                        birdTable.queryOne(i.toLong()).getFirst() shouldBe birds[i]
                    }
                }.shouldBeSuccess()
            }
            withClue("random access sightings") {
                db.transaction(sightingTable) {
                    for (i in sightings.indices.shuffled()) {
                        sightingTable.queryOne(i.toLong()).getFirst() shouldBe sightings[i]
                    }
                }.shouldBeSuccess()
            }

            // Random index access
            db.transaction(birdTable) {
                val minWingspan = birds.minOf { it.wingspanM }
                val maxWingspan = birds.maxOf { it.wingspanM }
                for (i in 0 until 1000) {
                    val rangeMin = doubleToFloat(Random.nextDouble(minWingspan.toDouble(), maxWingspan.toDouble()))
                    val rangeMax = doubleToFloat(Random.nextDouble(rangeMin.toDouble(), maxWingspan.toDouble()))
                    val expect = birds.filter { it.wingspanM in rangeMin..rangeMax }.sortedBy { it.wingspanM }
                    withClue({"$i Wingspan $rangeMin..$rangeMax"}) {
                        withClue("normal") { wingspanIndex.query(rangeMin, rangeMax, increasing = true).getAll() shouldContainExactly expect }
                        withClue("limited") { wingspanIndex.query(rangeMin, rangeMax, increasing = true).getAll(5) shouldContainExactly expect.take(5) }
                        withClue("reversed") { wingspanIndex.query(rangeMin, rangeMax, increasing = false).getAll() shouldContainExactly expect.asReversed() }
                        withClue("reversed limited") { wingspanIndex.query(rangeMin, rangeMax, increasing = false).getAll(5) shouldContainExactly expect.asReversed().take(5) }
                    }
                }
                wingspanIndex
            }.shouldBeSuccess()
        }
    }

    test("rollback add and set") {
        withDatabase(config) { db ->
            db.writeTransaction(birdTable) {
                birdTable.add(5, birds[0])
                birdTable.set(6, birds[1])
                fail("force rollback")
            }.shouldBeFailure()

            db.transaction(birdTable) {
                birdTable.queryOne(5).getFirst().shouldBeNull()
                birdTable.queryOne(6).getFirst().shouldBeNull()
                birdTable.queryAll().count() shouldBe 0
            }.shouldBeSuccess()
        }
    }

    test("cursor update and delete") {
        withDatabase(config) { db ->
            db.writeTransaction(birdTable) {
                birdTable.add(5, birds[0])
            }.shouldBeSuccess()

            db.writeTransaction(birdTable) {
                birdTable.queryAll().writeIterate().collect { cursor ->
                    cursor.update(birds[1])
                }
            }.shouldBeSuccess()

            db.transaction(birdTable) {
                birdTable.queryOne(5).getFirst() shouldBe birds[1]
                birdTable.queryAll().count() shouldBe 1
            }.shouldBeSuccess()

            db.writeTransaction(birdTable) {
                birdTable.queryAll().writeIterate().collect { cursor ->
                    cursor.delete()
                }
            }.shouldBeSuccess()

            db.transaction(birdTable) {
                birdTable.queryOne(5).getFirst() shouldBe null
                birdTable.queryAll().count() shouldBe 0
            }.shouldBeSuccess()
        }
    }

    test("rollback cursor update") {
        withDatabase(config) { db ->
            db.writeTransaction(birdTable) {
                birdTable.add(5, birds[0])
            }.shouldBeSuccess()

            db.writeTransaction(birdTable) {
                birdTable.queryAll().writeIterate().collect { cursor ->
                    cursor.delete()
                }
                fail("rollback")
            }.shouldBeFailure()

            db.writeTransaction(birdTable) {
                birdTable.queryAll().writeIterate().collect { cursor ->
                    cursor.update(birds[1])
                }
                fail("rollback")
            }.shouldBeFailure()

            db.transaction(birdTable) {
                birdTable.queryOne(5).getFirst() shouldBe birds[0]
                birdTable.queryAll().count() shouldBe 1
            }.shouldBeSuccess()
        }
    }

    test("read transactions are parallel") {
        withDatabase(config) { db ->
            coroutineScope {
                val tickets = mutableListOf("a", "b", "c", "a", "c", "b", "a", "b", "c")
                val jobs = listOf("a", "b", "c").map { jobName ->
                    launch {
                        db.transaction(birdTable) {
                            while (tickets.isNotEmpty()) {
                                if (tickets.last() == jobName) {
                                    tickets.removeLast()
                                } else {
                                    // Must delay, but we are in a transaction, only suspend functions allowed are DB, so do something, just to yield
                                    birdTable.queryAll().count() shouldBe 0
                                }
                            }
                        }.shouldBeSuccess()
                    }
                }
                withTimeout(1000) {
                    jobs.joinAll()
                }
                tickets.shouldBeEmpty()
            }
        }
    }

    test("write transactions are exclusive") {
        withDatabase(config) { db ->
            coroutineScope {
                var activeTransaction = "none"
                val jobs = listOf("a", "b", "c").map { jobName ->
                    launch {
                        db.writeTransaction(birdTable) {
                            // The exclusivity starts at first suspension point
                            birdTable.queryAll().count() shouldBe 0

                            activeTransaction shouldBe "none"
                            activeTransaction = jobName

                            // Must delay, but we are in a transaction, only suspend functions allowed are DB, so do something, just to yield
                            for (i in 0 until Random.nextInt(1, 100)) {
                                birdTable.queryAll().count() shouldBe 0
                            }

                            activeTransaction shouldBe jobName
                            activeTransaction = "none"
                        }.shouldBeSuccess()
                    }
                }
                withTimeout(1000) {
                    jobs.joinAll()
                }

                activeTransaction shouldBe "none"
            }
        }
    }

    // Test is disabled for now, because the test browsers have insanely large quotas by default and it does not work
    // I have tried to set the quotas manually in karma-config, but to no avail
    xtest("handle quotas") {
        val dataTable = Table("Data", IntKeySerializer, CborSerializers.BlobSerializer, emptyList())
        val blockConfig = BackendDatabaseConfig(config.name, Schema(1, listOf(dataTable)))

        val estimate = window.navigator.asDynamic().storage.estimate().unsafeCast<Promise<dynamic>>().await()
        if (true) throw RuntimeException("Estimate: ${JSON.stringify(estimate)}")

        withDatabase(blockConfig) { db ->
            // Attempt to insert infinite data to hit quota
            var blocks = 0
            val blockMB = 10
            val dataBlock = ByteArray(1024 * 1024 * blockMB) { it.toByte() }
            withClue({"Managed to fit $blocks MB"}) {
                while (true) {
                    db.writeTransaction(dataTable) {
                        dataTable.add(blocks++, dataBlock)
                    }//TODO
                }
            }
        }
    }

})

private fun <V:Any> Table<Long, V>.testQueries(toGetId: Long): List<Pair<String, Query<Long, Nothing, V>>> {
    return listOf(
        "queryOne" to queryOne(toGetId),
        "queryAll" to queryAll(),
        "queryAll(dec)" to queryAll(false),
        "query(+-0)" to query(toGetId, toGetId),
        "query(+-0, dec)" to query(toGetId, toGetId, increasing=false),
        "query(+-1)" to query(toGetId-1, toGetId+1),
        "query(+-1, open, open)" to query(toGetId-1, toGetId+1, openMin = true, openMax = true),
        "query(+1, closed, open)" to query(toGetId, toGetId+1, openMin = false, openMax = true),
        "query(+-1, closed, open)" to query(toGetId-1, toGetId+1, openMin = false, openMax = true),
        "query(+-1, open, closed)" to query(toGetId-1, toGetId+1, openMin = true, openMax = false),
        "query(-1, open, closed)" to query(toGetId-1, toGetId, openMin = true, openMax = false),
        "query(+-1, open, open, dec)" to query(toGetId-1, toGetId+1, openMin = true, openMax = true, increasing = false),
        "query(+1, closed, open, dec)" to query(toGetId, toGetId+1, openMin = false, openMax = true, increasing = false),
        "query(+-1, closed, open, dec)" to query(toGetId-1, toGetId+1, openMin = false, openMax = true, increasing = false),
        "query(+-1, open, closed, dec)" to query(toGetId-1, toGetId+1, openMin = true, openMax = false, increasing = false),
        "query(-1, open, closed)" to query(toGetId-1, toGetId, openMin = true, openMax = false),
        "query(+-1, dec)" to query(toGetId-1, toGetId+1, increasing=false),
        "query(+-1000)" to query(toGetId - 1000, toGetId + 1000),
        "query(+-1000, dec)" to query(toGetId - 1000, toGetId + 1000, increasing=false),
    )
}