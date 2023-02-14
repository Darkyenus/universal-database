package com.darkyen.ud

actual class BackendDatabaseConfig(
    name: String,
    vararg schema: Schema,
    /** Called once when the opening is blocked because of concurrent accesses. */
    internal val onOpeningBlocked: (() -> Unit)? = null,
    /** Called when another user of the DB wants to upgrade the database and requests us to close it */
    internal val onVersionChangeRequest: ((Database) -> Unit)? = null,
    /** Called when the database connection was closed forcefully (typically due to an error) */
    internal val onForceClose: (() -> Unit)? = null,
    )
    : BaseDatabaseConfig(name, *schema)

actual suspend fun openUniversalDatabase(config: BackendDatabaseConfig): OpenDBResult {
    return openIndexedDBUD(config)
}

actual suspend fun deleteUniversalDatabase(config: BackendDatabaseConfig) {
    deleteIndexedDBUD(config)
}
