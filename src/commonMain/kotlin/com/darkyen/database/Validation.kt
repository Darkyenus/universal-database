package com.darkyen.database

internal fun validateName(name: String) {
    if (name.length !in 1..100) throw IllegalArgumentException("name has unreasonable length")
    if (name.any { (it !in 'a'..'z') && (it !in 'A'..'Z') && (it !in '0'..'9') }) throw IllegalArgumentException("name contains unreasonable characters")
}

internal fun validateSchemaSequence(schemaVersions: Array<out Schema>) {
    if (schemaVersions.isEmpty()) throw IllegalArgumentException("no default schema")
    var lastSchema: Schema? = null
    for (schema in schemaVersions) {
        val tableNames = HashSet<String>()
        for (table in schema.tables) {
            if (!tableNames.add(table.name)) {
                throw IllegalArgumentException("table ${table.name} duplicated")
            }
        }

        if (lastSchema == null) {
            if (schema.version < 1) throw IllegalArgumentException("schema.version < 1")
            lastSchema = schema
            continue
        } else if (schema.version != lastSchema.version + 1) {
            throw IllegalArgumentException("schema.version != ${lastSchema.version + 1}")
        }

        for (oldTable in lastSchema.tables) {
            val newTable = schema.tables.find { it.name == oldTable.name }
            if (newTable != null && newTable !== oldTable) throw IllegalArgumentException("table ${newTable.name} changed identity in version ${schema.version}")
        }

        lastSchema = schema
    }
}