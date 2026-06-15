package cat.subi.itaca.ingestion.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

/** Raw-SQL persistence for the ingestion registry (CQRS-light read/write side). */
@Repository
class IngestedFileRepository(
    private val jdbc: JdbcTemplate,
) {
    fun insert(
        source: String,
        name: String,
        type: String,
        storagePath: String,
    ): Long =
        jdbc.queryForObject(
            """
            INSERT INTO ingested_files (source, name, type, status, storage_path)
            VALUES (?, ?, ?, 'pending', ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            source,
            name,
            type,
            storagePath,
        )!!

    fun find(id: Long): IngestedFile? = jdbc.query("$SELECT WHERE id = ?", MAPPER, id).firstOrNull()

    fun recent(limit: Int): List<IngestedFile> = jdbc.query("$SELECT ORDER BY id DESC LIMIT ?", MAPPER, limit)

    fun setDestination(
        id: Long,
        destination: String?,
    ) {
        jdbc.update("UPDATE ingested_files SET destination = ? WHERE id = ?", destination, id)
    }

    fun markProcessed(
        id: Long,
        detail: String,
    ) {
        jdbc.update(
            "UPDATE ingested_files SET status = 'processed', detail = ?, error_message = NULL WHERE id = ?",
            detail,
            id,
        )
    }

    fun markError(
        id: Long,
        reason: String,
    ) {
        jdbc.update("UPDATE ingested_files SET status = 'error', error_message = ? WHERE id = ?", reason, id)
    }

    fun resetForRetry(id: Long) {
        jdbc.update(
            "UPDATE ingested_files SET status = 'pending', error_message = NULL, detail = NULL WHERE id = ?",
            id,
        )
    }

    private companion object {
        const val SELECT = "SELECT * FROM ingested_files"

        val MAPPER =
            RowMapper { rs, _ ->
                IngestedFile(
                    id = rs.getLong("id"),
                    source = rs.getString("source"),
                    name = rs.getString("name"),
                    type = rs.getString("type"),
                    destination = rs.getString("destination"),
                    status = rs.getString("status"),
                    storagePath = rs.getString("storage_path"),
                    errorMessage = rs.getString("error_message"),
                    detail = rs.getString("detail"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
    }
}
