package cat.subi.itaca.health.application

import cat.subi.itaca.health.adapter.out.persistence.LabReportRepository
import cat.subi.itaca.health.adapter.out.persistence.MedicalDocumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Controlled theme vocabulary for reports and documents (display labels live in the UI). */
val REPORT_CATEGORIES = setOf("ibd", "fertility", "general", "other")

/** Normalizes a free extracted/edited category to the controlled set; unknown -> null. */
fun normalizeCategory(raw: String?): String? = raw?.trim()?.lowercase()?.takeIf { it in REPORT_CATEGORIES }

/**
 * Sets the theme category on a report or document. Kept apart from the large
 * pipeline services (which are at detekt's function-count limit) as its own concern.
 */
@Service
class CategoryService(
    private val reports: LabReportRepository,
    private val documents: MedicalDocumentRepository,
) {
    @Transactional
    fun setReportCategory(
        reportId: Long,
        category: String?,
    ) {
        val report = reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        report.category = normalizeCategory(category)
        reports.save(report)
    }

    @Transactional
    fun setDocumentCategory(
        documentId: Long,
        category: String?,
    ) {
        val document =
            documents.findById(documentId).orElseThrow {
                NoSuchElementException("Medical document $documentId not found")
            }
        document.category = normalizeCategory(category)
        documents.save(document)
    }
}
