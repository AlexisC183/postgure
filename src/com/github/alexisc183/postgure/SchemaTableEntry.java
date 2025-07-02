package com.github.alexisc183.postgure;

/**
 * Value-based type used to find already-created objects with the provided database schema and table.
 * 
 * @apiNote This API is intended for internal use.
 * @author AlexisC183
 * @version 1, 2025-07-02
 * @param schema the database schema
 * @param table the database table
 * @since postgure 1.0.0
 */
record SchemaTableEntry(String schema, String table) {

}
