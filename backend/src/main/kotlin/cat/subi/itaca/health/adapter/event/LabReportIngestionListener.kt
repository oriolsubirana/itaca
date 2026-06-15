package cat.subi.itaca.health.adapter.event

import cat.subi.itaca.health.adapter.jobs.ExtractLabReportRequest
import cat.subi.itaca.health.application.LabReportService
import cat.subi.itaca.ingestion.IngestionFailed
import cat.subi.itaca.ingestion.IngestionSucceeded
import cat.subi.itaca.ingestion.LabReportReceived
import cat.subi.itaca.shared.storage.DocumentStorage
import org.jobrunr.scheduling.JobRequestScheduler
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Health's reaction to an ingested lab report: load the bytes, create the report and
 * enqueue extraction (the same path as a manual upload), then report the outcome back
 * to ingestion. Idempotent enough for redelivery — a duplicate just creates a re-run.
 */
@Component
class LabReportIngestionListener(
    private val labReports: LabReportService,
    private val storage: DocumentStorage,
    private val jobs: JobRequestScheduler,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(LabReportIngestionListener::class.java)

    // Any downstream failure must surface in the inbox as an IngestionFailed, hence the broad catch.
    @Suppress("TooGenericExceptionCaught")
    @ApplicationModuleListener
    fun on(event: LabReportReceived) {
        try {
            val content = storage.load(event.storagePath)
            val report = labReports.upload(event.filename, content)
            jobs.enqueue(ExtractLabReportRequest(report.id))
            events.publishEvent(IngestionSucceeded(event.ingestionId, "Analítica recibida; extrayendo resultados."))
        } catch (e: RuntimeException) {
            log.warn("Lab report ingestion failed for {}", event.ingestionId, e)
            events.publishEvent(IngestionFailed(event.ingestionId, e.message ?: "No pude procesar la analítica."))
        }
    }
}
