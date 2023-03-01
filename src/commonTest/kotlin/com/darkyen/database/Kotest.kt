package com.darkyen.database

import io.kotest.core.spec.style.FunSpec

expect fun FunSpec.databaseTest(name: String, schema: Schema, test: suspend (BackendDatabaseConfig) -> Unit)

expect fun FunSpec.databaseTest(name: String, schema: List<Schema>, test: suspend (List<BackendDatabaseConfig>) -> Unit)

expect suspend fun doSomethingSuspending(kind: Int)
