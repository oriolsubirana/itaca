package cat.subi.itaca.ingestion.adapter.event

import cat.subi.itaca.ingestion.IngestionFailed
import cat.subi.itaca.ingestion.IngestionSucceeded
import cat.subi.itaca.ingestion.application.IngestionService
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Flips the registry row when a consuming context reports back. Runs after commit,
 * async, with registry-backed redelivery; the status updates are idempotent.
 */
@Component
class IngestionOutcomeListener(
    private val ingestion: IngestionService,
) {
    @ApplicationModuleListener
    fun on(event: IngestionSucceeded) {
        ingestion.markProcessed(event.ingestionId, event.detail)
    }

    @ApplicationModuleListener
    fun on(event: IngestionFailed) {
        ingestion.markFailed(event.ingestionId, event.reason)
    }
}
