package cat.subi.itaca.ingestion.application

import cat.subi.itaca.ingestion.BankStatementReceived
import cat.subi.itaca.ingestion.IngestionFailed
import cat.subi.itaca.ingestion.IngestionSucceeded
import cat.subi.itaca.ingestion.LabReportReceived
import cat.subi.itaca.ingestion.domain.Destination
import cat.subi.itaca.ingestion.domain.FileType
import cat.subi.itaca.ingestion.domain.FileTypeDetector
import cat.subi.itaca.shared.storage.DocumentStorage
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Generic file intake: store the file, register it, and (async) classify + route it
 * to the owning context via Modulith events. The contexts report the outcome back
 * through [IngestionSucceeded] / [IngestionFailed], which flip the registry row.
 */
@Service
class IngestionService(
    private val repo: IngestedFileRepository,
    private val storage: DocumentStorage,
    private val classifier: IngestionClassifier,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(IngestionService::class.java)

    /** Stores the file and registers it as pending; the caller enqueues [process]. */
    @Transactional
    fun ingest(
        source: String,
        filename: String,
        content: ByteArray,
    ): IngestedFileDto {
        val type = FileTypeDetector.detect(filename, content)
        val path = storage.store(filename, content)
        val id = repo.insert(source, filename, type.name.lowercase(), path)
        return repo.find(id)!!.toDto()
    }

    /** Classifies the file and publishes the routing event (or marks it as an error). */
    @Transactional
    fun process(id: Long) {
        val file = repo.find(id) ?: return
        val content = storage.load(file.storagePath)
        val type = FileType.entries.firstOrNull { it.name.equals(file.type, ignoreCase = true) } ?: FileType.UNKNOWN
        when (classifier.classify(file.name, type, content)) {
            Destination.HEALTH_LAB -> {
                repo.setDestination(id, "health")
                events.publishEvent(LabReportReceived(id, file.name, file.storagePath))
            }
            Destination.FINANCE_BANK -> {
                repo.setDestination(id, "finance")
                events.publishEvent(BankStatementReceived(id, file.name, file.storagePath))
            }
            Destination.UNKNOWN -> {
                log.info("Could not classify ingested file {} ({})", id, file.name)
                repo.markError(id, "No pude identificar el tipo de documento. Súbelo desde su sección.")
            }
        }
    }

    @Transactional
    fun markProcessed(
        id: Long,
        detail: String,
    ) = repo.markProcessed(id, detail)

    @Transactional
    fun markFailed(
        id: Long,
        reason: String,
    ) = repo.markError(id, reason)

    /** Resets a file to pending so the caller can re-enqueue [process]. */
    @Transactional
    fun retry(id: Long): IngestedFileDto {
        val file = repo.find(id) ?: throw NoSuchElementException("Ingested file $id not found")
        repo.resetForRetry(file.id)
        return repo.find(id)!!.toDto()
    }

    fun recent(): List<IngestedFileDto> = repo.recent(RECENT_LIMIT).map { it.toDto() }

    private companion object {
        const val RECENT_LIMIT = 50
    }
}
