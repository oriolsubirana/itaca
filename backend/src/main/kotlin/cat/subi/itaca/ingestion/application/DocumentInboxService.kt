package cat.subi.itaca.ingestion.application

import cat.subi.itaca.ingestion.DocumentInbox
import cat.subi.itaca.ingestion.adapter.jobs.ProcessIngestionRequest
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.stereotype.Service

/**
 * Default [DocumentInbox]: stores + registers the document via [IngestionService] and enqueues the
 * async classify/route step — the same two steps POST /api/ingest performs, exposed for in-process
 * callers like the Drive watcher.
 */
@Service
class DocumentInboxService(
    private val ingestion: IngestionService,
    private val jobs: JobRequestScheduler,
) : DocumentInbox {
    override fun receive(
        source: String,
        filename: String,
        content: ByteArray,
    ): Long {
        val dto = ingestion.ingest(source, filename, content)
        jobs.enqueue(ProcessIngestionRequest(dto.id))
        return dto.id
    }
}
