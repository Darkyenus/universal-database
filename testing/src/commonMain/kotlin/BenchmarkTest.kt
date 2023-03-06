package com.darkyen.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

object BenchmarkTest : TestContainer ({
    val conservationIndex = Index<Long, ConservationStatus, Bird>("Conservation", { _, v -> v.conservationStatus }, ConservationStatus.KEY_SERIALIZER, false)
    val birdTable = Table("Birds", LongKeySerializer, BIRD_SERIALIZER, listOf(
        conservationIndex
    ))
    val schema = Schema(1, listOf(birdTable))
    val birds = birds

    fun fullBenchmark(name:String, amount: Int) {
        databaseTest(name, schema) { config ->
            withDatabase(config) { db ->
                val id = (0 until amount).shuffled()

                benchmark("insert", id.size) {
                    for (t in id) {
                        db.writeTransaction(birdTable) {
                            birdTable.add(t.toLong(), birds.random())
                        }.getOrThrow()
                    }
                }

                benchmark("update", id.size) {
                    for (t in id) {
                        db.writeTransaction(birdTable) {
                            birdTable.set(t.toLong(), birds.random())
                        }.getOrThrow()
                    }
                }

                benchmark("update chunked", id.size) {
                    for (ts in id.chunked(50)) {
                        db.writeTransaction(birdTable) {
                            for (t in ts) {
                                birdTable.set(t.toLong(), birds.random())
                            }
                        }.getOrThrow()
                    }
                }

                benchmark("select all in 10 chunks", id.size * 10) {
                    for (t in id) {
                        db.transaction(birdTable) {
                            birdTable.query(t.toLong(), null).getAll(10)
                        }.getOrThrow()
                    }
                }

                benchmark("select all cursor", id.size) {
                    db.transaction(birdTable) {
                        birdTable.queryAll().iterate().map { it.value }.collect {}
                    }.getOrThrow()
                }

                benchmark("select one getFirst", id.size) {
                    for (t in id) {
                        db.transaction(birdTable) {
                            birdTable.queryOne(t.toLong()).getFirst()
                        }.getOrThrow()
                    }
                }

                benchmark("select more getFirst", id.size) {
                    for (t in id) {
                        db.transaction(birdTable) {
                            birdTable.query(t.toLong(), (t + 10).toLong()).getFirst()
                        }.getOrThrow()
                    }
                }

                benchmark("select cursor first", id.size) {
                    for (t in id) {
                        db.transaction(birdTable) {
                            birdTable.query(t.toLong(), (t + 10).toLong()).iterate().map { it.value }.first()
                        }.getOrThrow()
                    }
                }

                benchmark("delete", id.size) {
                    for (t in id) {
                        db.writeTransaction(birdTable) {
                            birdTable.queryOne(t.toLong()).delete()
                        }.getOrThrow()
                    }
                }
            }
        }
    }

    fullBenchmark("benchmark 01 cold 100", 100)
    fullBenchmark("benchmark 02 warm 1000", 1000)
    fullBenchmark("benchmark 03 hot 10 000", 10_000)
})