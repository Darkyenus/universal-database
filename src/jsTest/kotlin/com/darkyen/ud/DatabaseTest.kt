package com.darkyen.ud

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.mpp.timeInMillis
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

class DatabaseBirdTest : FunSpec({

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
                            query.getAll() shouldBe listOf()
                            query.getAll(10) shouldBe listOf()
                            query.getAll(1) shouldBe listOf()
                            query.getAll(0) shouldBe listOf()
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
                            query.getAll() shouldBe listOf()
                            query.getAll(10) shouldBe listOf()
                            query.getAll(1) shouldBe listOf()
                            query.getAll(0) shouldBe listOf()
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
                }.shouldBeFailure<ConstraintError>()
                db.writeTransaction(sightingTable) {
                    sightingTable.add(SIGHTING_ID, sighting)
                }.shouldBeFailure<ConstraintError>()
            }
            withClue("adding same unique index") {
                db.writeTransaction(birdTable) {
                    birdTable.add(6, bird)
                }.shouldBeFailure<ConstraintError>()
            }
            withClue("get it") {
                db.transaction(birdTable) {
                    for ((name, query) in birdTable.testQueries(BIRD_ID)) {
                        withClue("bird $name") {
                            withClue("count") { query.count() shouldBe 1 }
                            withClue("getFirst") { query.getFirst() shouldBe bird }
                            withClue("getAll") { query.getAll() shouldBe listOf(bird) }
                            withClue("getAll(10)") { query.getAll(10) shouldBe listOf(bird) }
                            withClue("getAll(1)") { query.getAll(1) shouldBe listOf(bird) }
                            withClue("getAll(0)") { query.getAll(0) shouldBe listOf(bird) }
                            withClue("getAll(-5)") { query.getAll(-5) shouldBe listOf(bird) }
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
                            withClue("getAll") { query.getAll() shouldBe listOf(sighting) }
                            withClue("getAll(10)") { query.getAll(10) shouldBe listOf(sighting) }
                            withClue("getAll(1)") { query.getAll(1) shouldBe listOf(sighting) }
                            withClue("getAll(0)") { query.getAll(0) shouldBe listOf(sighting) }
                            withClue("getAll(-5)") { query.getAll(-5) shouldBe listOf(sighting) }
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
                    withClue("bird getAll") { birdTable.queryOne(88).getAll() shouldBe listOf() }
                    withClue("bird getAll") { birdTable.query(BIRD_ID, 88, openMin = true).getAll() shouldBe listOf() }
                    withClue("bird getAll") { birdTable.query(BIRD_ID, 88, openMin = true, increasing = false).getAll() shouldBe listOf() }
                }.shouldBeSuccess()
                db.transaction(sightingTable) {
                    withClue("bird count") { sightingTable.queryOne(88).count() shouldBe 0 }
                    withClue("bird getFirst") { sightingTable.queryOne(88).getFirst() shouldBe null }
                    withClue("bird getAll") { sightingTable.queryOne(88).getAll() shouldBe listOf() }
                    withClue("bird getAll") { sightingTable.query(SIGHTING_ID, 88, openMin = true).getAll() shouldBe listOf() }
                    withClue("bird getAll") { sightingTable.query(SIGHTING_ID, 88, openMin = true, increasing = false).getAll() shouldBe listOf() }
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