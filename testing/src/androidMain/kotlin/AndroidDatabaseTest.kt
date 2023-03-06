package com.darkyen.database

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlin.random.Random

object AndroidDatabaseTest : TestContainer({

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

    databaseTest("read transactions are parallel", schema) { config ->
        withDatabase(config) { db ->
            coroutineScope {
                val tickets = mutableListOf("a", "b", "c", "a", "c", "b", "a", "b", "c")
                val jobs = listOf("a", "b", "c").map { jobName ->
                    launch {
                        db.transaction(birdTable) {
                            while (true) {
                                val breakLoop = synchronized(tickets) {
                                    if (tickets.isEmpty()) true
                                    else if (tickets.last() == jobName) {
                                        tickets.removeLast()
                                        false
                                    } else null
                                }
                                when (breakLoop) {
                                    true -> break
                                    false -> continue
                                    null -> yield()
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

    databaseTest("write transactions are exclusive", schema) { config ->
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
                                delay(1)
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