package cat.subi.itaca.ingestion.domain

/**
 * Processing status of a file ingested via /api/ingest. [wire] is the lowercase form
 * persisted in `ingested_files.status` (matched by the table's CHECK constraint).
 */
enum class IngestionStatus {
    PENDING,
    PROCESSED,
    ERROR,
    ;

    val wire: String get() = name.lowercase()
}
