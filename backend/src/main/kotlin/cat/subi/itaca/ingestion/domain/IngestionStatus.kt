package cat.subi.itaca.ingestion.domain

/**
 * Estado de procesado de un fichero ingerido vía /api/ingest.
 */
enum class IngestionStatus { PENDIENTE, PROCESADO, ERROR }
