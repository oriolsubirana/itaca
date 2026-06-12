package cat.subi.itaca.health.application

import cat.subi.itaca.health.adapter.out.anthropic.MedicalDocumentExtractor
import cat.subi.itaca.health.adapter.out.persistence.MedicalDiagnosisEntity
import cat.subi.itaca.health.adapter.out.persistence.MedicalDiagnosisRepository
import cat.subi.itaca.health.adapter.out.persistence.MedicalDocumentEntity
import cat.subi.itaca.health.adapter.out.persistence.MedicalDocumentRepository
import cat.subi.itaca.health.adapter.out.persistence.MedicalMedicationEntity
import cat.subi.itaca.health.adapter.out.persistence.MedicalMedicationRepository
import cat.subi.itaca.health.adapter.out.storage.DocumentStorage
import cat.subi.itaca.health.adapter.out.storage.StoredFile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

data class MedicalDocumentDto(
    val id: Long,
    val date: String?,
    val type: String?,
    val provider: String?,
    val center: String?,
    val filename: String?,
    val status: String,
    val category: String?,
    val extracting: Boolean,
    val diagnosisCount: Int,
    val medicationCount: Int,
)

data class MedicalDiagnosisDto(
    val id: Long,
    val code: String?,
    val label: String,
    val date: String?,
)

data class MedicalMedicationDto(
    val id: Long,
    val name: String,
    val dose: String?,
    val schedule: String?,
    val duration: String?,
    val reason: String?,
)

data class MedicalDocumentDetail(
    val document: MedicalDocumentDto,
    val diagnoses: List<MedicalDiagnosisDto>,
    val medications: List<MedicalMedicationDto>,
    val fullText: String?,
)

/**
 * Clinical document pipeline: upload -> async extraction (JobRunr + claude-haiku) ->
 * manual review -> confirm. Records facts only; it never interprets diagnoses.
 */
@Service
class MedicalDocumentService(
    private val documents: MedicalDocumentRepository,
    private val diagnoses: MedicalDiagnosisRepository,
    private val medications: MedicalMedicationRepository,
    private val storage: DocumentStorage,
    private val extractor: MedicalDocumentExtractor,
) {
    private val log = LoggerFactory.getLogger(MedicalDocumentService::class.java)

    @Transactional
    fun upload(
        filename: String,
        content: ByteArray,
    ): MedicalDocumentDto {
        val path = storage.store(filename, content)
        val document =
            documents.save(MedicalDocumentEntity(filename = filename, storagePath = path, extracting = true))
        return document.toDto(0, 0)
    }

    /** Executed by the JobRunr handler, with retries on failure. */
    @Transactional
    fun runExtraction(documentId: Long) {
        val document = documents.findById(documentId).orElseThrow { notFound(documentId) }
        val pdf = storage.load(checkNotNull(document.storagePath) { "Document $documentId has no stored file" })
        val extraction = extractor.extract(pdf)

        // Serialize concurrent jobs for the same document only around the writes.
        documents.lockById(documentId) ?: throw notFound(documentId)
        extraction.date?.let { runCatching { document.docDate = LocalDate.parse(it) }.getOrNull() }
        document.type = extraction.type?.takeIf { it.isNotBlank() }
        document.provider = extraction.provider?.takeIf { it.isNotBlank() }
        document.center = extraction.center?.takeIf { it.isNotBlank() }
        document.fullText = extraction.fullText?.takeIf { it.isNotBlank() }
        normalizeCategory(extraction.category)?.let { document.category = it }
        document.extracting = false
        documents.save(document)

        diagnoses.deleteByDocumentId(documentId) // idempotent re-runs (JobRunr retries)
        medications.deleteByDocumentId(documentId)
        extraction.diagnoses
            .filter { it.label.isNotBlank() }
            .forEach { row ->
                diagnoses.save(
                    MedicalDiagnosisEntity(
                        documentId = documentId,
                        code = row.code?.takeIf { it.isNotBlank() },
                        label = row.label,
                        diagnosedOn = row.date?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() },
                    ),
                )
            }
        extraction.medications
            .filter { it.name.isNotBlank() }
            .forEach { row ->
                medications.save(
                    MedicalMedicationEntity(
                        documentId = documentId,
                        name = row.name,
                        dose = row.dose?.takeIf { it.isNotBlank() },
                        schedule = row.schedule?.takeIf { it.isNotBlank() },
                        duration = row.duration?.takeIf { it.isNotBlank() },
                        reason = row.reason?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        log.info(
            "Extracted {} diagnoses and {} medications from medical document {}",
            extraction.diagnoses.size,
            extraction.medications.size,
            documentId,
        )
    }

    fun recentDocuments(): List<MedicalDocumentDto> =
        documents.findForList().map {
            it.toDto(
                diagnoses.findByDocumentIdOrderById(it.id!!).size,
                medications.findByDocumentIdOrderById(it.id!!).size,
            )
        }

    fun detail(documentId: Long): MedicalDocumentDetail {
        val document = documents.findById(documentId).orElseThrow { notFound(documentId) }
        val dx = diagnoses.findByDocumentIdOrderById(documentId)
        val meds = medications.findByDocumentIdOrderById(documentId)
        return MedicalDocumentDetail(
            document.toDto(dx.size, meds.size),
            dx.map { MedicalDiagnosisDto(it.id!!, it.code, it.label, it.diagnosedOn?.toString()) },
            meds.map { MedicalMedicationDto(it.id!!, it.name, it.dose, it.schedule, it.duration, it.reason) },
            document.fullText,
        )
    }

    fun loadFile(documentId: Long): StoredFile {
        val document = documents.findById(documentId).orElseThrow { notFound(documentId) }
        val path = checkNotNull(document.storagePath) { "Document $documentId has no stored file" }
        return StoredFile(document.filename ?: "documento.pdf", storage.load(path))
    }

    @Transactional
    fun review(
        documentId: Long,
        confirm: Boolean,
    ): MedicalDocumentDto {
        val document = documents.findById(documentId).orElseThrow { notFound(documentId) }
        document.status = if (confirm) "confirmed" else "discarded"
        documents.save(document)
        return detailCounts(document)
    }

    /** Re-queues a document for extraction; existing facts are replaced by the job. */
    @Transactional
    fun prepareReextraction(documentId: Long): MedicalDocumentDto {
        val document = documents.findById(documentId).orElseThrow { notFound(documentId) }
        require(document.status != "confirmed") { "Confirmed documents cannot be re-extracted" }
        document.status = "pending_review"
        document.extracting = true
        documents.save(document)
        return detailCounts(document)
    }

    @Transactional
    fun deleteDocument(documentId: Long) {
        if (!documents.existsById(documentId)) throw notFound(documentId)
        diagnoses.deleteByDocumentId(documentId)
        medications.deleteByDocumentId(documentId)
        documents.deleteById(documentId)
    }

    private fun detailCounts(document: MedicalDocumentEntity): MedicalDocumentDto {
        val id = document.id!!
        return document.toDto(
            diagnoses.findByDocumentIdOrderById(id).size,
            medications.findByDocumentIdOrderById(id).size,
        )
    }

    private fun notFound(documentId: Long) = NoSuchElementException("Medical document $documentId not found")

    private fun MedicalDocumentEntity.toDto(
        diagnosisCount: Int,
        medicationCount: Int,
    ) = MedicalDocumentDto(
        id = id!!,
        date = docDate?.toString(),
        type = type,
        provider = provider,
        center = center,
        filename = filename,
        status = status,
        category = category,
        extracting = extracting,
        diagnosisCount = diagnosisCount,
        medicationCount = medicationCount,
    )
}
