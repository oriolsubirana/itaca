package cat.subi.itaca.ingestion

/**
 * Inbound port of the ingestion context: hand a document to the pipeline (store, register, and
 * asynchronously classify + route it to health/finance). Exposed in the module's top-level package
 * so other contexts can feed documents in — e.g. the Google Drive folder watcher — without
 * depending on ingestion internals. Mirrors what POST /api/ingest does for uploads.
 */
interface DocumentInbox {
    /** Returns the ingestion id; processing happens asynchronously afterwards. */
    fun receive(
        source: String,
        filename: String,
        content: ByteArray,
    ): Long
}
