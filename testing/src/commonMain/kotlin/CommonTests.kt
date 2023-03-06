package com.darkyen.database

object CommonTests : TestContainer({
    include(DatabaseKeyTest())
    include(DatabaseMigrationTest())
    include(DatabaseTest())
    include(DatabaseObserverTest())
    include(BenchmarkTest)
})