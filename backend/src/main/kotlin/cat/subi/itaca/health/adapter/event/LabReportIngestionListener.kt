package cat.subi.itaca.health.adapter.event

import cat.subi.itaca.health.adapter.jobs.ExtractLabReportRequest
import cat.subi.itaca.health.application.LabReportService
import cat.subi.itaca.ingestion.IngestionFailed
import cat.subi.itaca.ingestion.IngestionSucceeded
import cat.subi.itaca.ingestion.LabReportReceived
import org.jobrunr.scheduling.JobRequestScheduler
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Health's reaction to an ingested lab report: register a report pointing at the file
 * ingestion already stored (no second copy), enqueue extraction, and report the outcome
 * back to ingestion. `registerStored` is idempotent on the storage path, so an event
 * redelivery or a retry does not create a duplicate report.
 */
@Component
class LabReportIngestionListener(
    private val labReports: LabReportService,
    private val jobs: JobRequestScheduler,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(LabReportIngestionListener::class.java)

    // Any downstream failure must surface in the inbox as an IngestionFailed, hence the broad catch.
    @Suppress("TooGenericExceptionCaught")
    @ApplicationModuleListener
    fun on(event: LabReportReceived) {
        try {
            val report = labReports.registerStored(event.filename, event.storagePath)
            jobs.enqueue(ExtractLabReportRequest(report.id))
            events.publishEvent(IngestionSucceeded(event.ingestionId, "Analítica recibida; extrayendo resultados."))
        } catch (e: RuntimeException) {
            log.warn("Lab report ingestion failed for {}", event.ingestionId, e)
            events.publishEvent(IngestionFailed(event.ingestionId, e.message ?: "No pude procesar la analítica."))
        }
    }
}
