package nart.simpleanki.core.apkg

/** In-memory [AnkiSqlite] for tests: maps an SQL string to canned rows. */
class FakeAnkiSqlite(private val responses: Map<String, List<Map<String, Any?>>>) : AnkiSqlite {
    var closed = false
    override fun query(sql: String): List<Map<String, Any?>> =
        responses[sql] ?: error("no canned response for SQL: $sql")
    override fun close() { closed = true }
}
