package cat.subi.itaca.ingestion

/*
 * Cross-context events for the ingestion flow. They are the ingestion module's
 * exposed API (top-level package, not an internal subpackage): health and finance
 * consume the *Received events and report back the outcome, while ingestion stays
 * unaware of those contexts — keeping the module graph acyclic.
 *
 * Events carry ids + the storage path only (never bytes): consumers reload the
 * content through the shared DocumentStorage, keeping the event registry light.
 */

/** A health lab report was received and should enter the extraction pipeline. */
data class LabReportReceived(
    val ingestionId: Long,
    val filename: String,
    val storagePath: String,
)

/** A clinical document (consultation letter, report, treatment plan...) was received for health. */
data class MedicalDocumentReceived(
    val ingestionId: Long,
    val filename: String,
    val storagePath: String,
)

/**
 * A bank statement / pension report was received and should be imported into finance.
 * Carries the content type already decided at routing ("pdf" = finpension, "csv" = Neon)
 * so the consumer doesn't re-classify the file.
 */
data class BankStatementReceived(
    val ingestionId: Long,
    val filename: String,
    val storagePath: String,
    val contentType: String,
)

/** A consuming context finished processing an ingested file successfully. */
data class IngestionSucceeded(
    val ingestionId: Long,
    val detail: String,
)

/** A consuming context could not process an ingested file. */
data class IngestionFailed(
    val ingestionId: Long,
    val reason: String,
)
