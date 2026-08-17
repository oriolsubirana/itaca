package cat.subi.itaca.health.adapter.event

import cat.subi.itaca.health.adapter.jobs.ExtractMedicalDocumentRequest
import cat.subi.itaca.health.application.MedicalDocumentService
import cat.subi.itaca.ingestion.IngestionFailed
import cat.subi.itaca.ingestion.IngestionSucceeded
import cat.subi.itaca.ingestion.MedicalDocumentReceived
import org.jobrunr.scheduling.JobRequestScheduler
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Health's reaction to an ingested clinical document (consultation letter, report, treatment
 * plan...): register a medical document pointing at the file ingestion already stored, enqueue
 * extraction (diagnoses / medications), and report the outcome back to ingestion. `registerStored`
 * is idempotent on the storage path, so a redelivery or retry does not create a duplicate.
 */
@Component
class MedicalDocumentIngestionListener(
    private val medicalDocuments: MedicalDocumentService,
    private val jobs: JobRequestScheduler,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(MedicalDocumentIngestionListener::class.java)

    // Any downstream failure must surface in the inbox as an IngestionFailed, hence the broad catch.
    @Suppress("TooGenericExceptionCaught")
    @ApplicationModuleListener
    fun on(event: MedicalDocumentReceived) {
        try {
            val document = medicalDocuments.registerStored(event.filename, event.storagePath)
            jobs.enqueue(ExtractMedicalDocumentRequest(document.id))
            events.publishEvent(
                IngestionSucceeded(event.ingestionId, "Documento clínico recibido; extrayendo información."),
            )
        } catch (e: RuntimeException) {
            log.warn("Medical document ingestion failed for {}", event.ingestionId, e)
            val reason = e.message ?: "No pude procesar el documento clínico."
            events.publishEvent(IngestionFailed(event.ingestionId, reason))
        }
    }
}
