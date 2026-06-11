package cat.subi.itaca.ingestion.domain

/**
 * Processing status of a file ingested via /api/ingest.
 */
enum class IngestionStatus { PENDING, PROCESSED, ERROR }
