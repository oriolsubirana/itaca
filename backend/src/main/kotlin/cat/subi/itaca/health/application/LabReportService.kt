package cat.subi.itaca.health.application

import cat.subi.itaca.health.adapter.out.anthropic.LabReportExtractor
import cat.subi.itaca.health.adapter.out.persistence.LabReportEntity
import cat.subi.itaca.health.adapter.out.persistence.LabReportRepository
import cat.subi.itaca.health.adapter.out.persistence.LabResultEntity
import cat.subi.itaca.health.adapter.out.persistence.LabResultRepository
import cat.subi.itaca.health.adapter.out.storage.LabFileStorage
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

data class LabReportDto(
    val id: Long,
    val date: String,
    val laboratory: String?,
    val filename: String?,
    val status: String,
    val extracting: Boolean,
    val resultCount: Int,
)

data class LabResultDto(
    val id: Long,
    val rawName: String,
    val analyteCode: String?,
    val analyteName: String?,
    // The result's own measurement date (cumulative reports); null inherits the report date.
    val date: String?,
    val value: Double?,
    val textValue: String?,
    val unit: String?,
    val refMin: Double?,
    val refMax: Double?,
    val reviewed: Boolean,
)

data class LabReportDetail(
    val report: LabReportDto,
    val results: List<LabResultDto>,
)

/** Partial update; null means "leave as is", empty string clears textValue/unit. */
data class LabResultUpdate(
    val date: String? = null,
    val value: Double? = null,
    val textValue: String? = null,
    val unit: String? = null,
    val analyteCode: String? = null,
    val reviewed: Boolean? = null,
)

/**
 * Lab report pipeline: upload -> async extraction (JobRunr + claude-haiku) ->
 * manual review -> confirm. Only confirmed reports feed the analyte series.
 */
@Service
class LabReportService(
    private val reports: LabReportRepository,
    private val results: LabResultRepository,
    private val storage: LabFileStorage,
    private val extractor: LabReportExtractor,
    private val matcher: AnalyteMatcher,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(LabReportService::class.java)

    /** Stores the PDF and creates the report; extraction runs asynchronously. */
    @Transactional
    fun upload(
        filename: String,
        content: ByteArray,
    ): LabReportDto {
        val path = storage.store(filename, content)
        val report =
            reports.save(
                LabReportEntity(date = LocalDate.now(), filename = filename, storagePath = path, extracting = true),
            )
        return report.toDto(0)
    }

    /** Executed by the JobRunr handler, with retries on failure. */
    @Transactional
    fun runExtraction(reportId: Long) {
        val report = reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        val pdf = storage.load(checkNotNull(report.storagePath) { "Report $reportId has no stored file" })
        val extraction = extractor.extract(pdf)

        // Serialize concurrent jobs for the same report only around the writes
        // (the row lock is not held during the slow extraction call above).
        reports.lockById(reportId) ?: throw NoSuchElementException("Lab report $reportId not found")
        extraction.date?.let { runCatching { report.date = LocalDate.parse(it) } }
        extraction.laboratory?.takeIf { it.isNotBlank() }?.let { report.laboratory = it }
        report.extracting = false
        reports.save(report)

        results.deleteByLabReportId(reportId) // idempotent re-runs (JobRunr retries)
        extraction.results
            .filter { it.analyte.isNotBlank() && (it.value != null || !it.textValue.isNullOrBlank()) }
            .forEach { row ->
                results.save(
                    LabResultEntity(
                        labReportId = reportId,
                        // The dictionary holds numeric blood markers; a qualitative row or a
                        // per-field microscopy count that shares a name (urine sediment, smear
                        // morphology...) is a different measurement. Normalize only numeric
                        // results whose unit is compatible with the analyte's canonical unit.
                        analyteId =
                            if (row.value != null) {
                                matcher.match(row.analyte)?.takeIf { unitsCompatible(row.unit, it.canonicalUnit) }?.id
                            } else {
                                null
                            },
                        rawName = row.analyte,
                        resultDate = row.date?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() },
                        value = row.value?.let(BigDecimal::valueOf),
                        textValue = row.textValue?.takeIf { it.isNotBlank() },
                        unit = row.unit,
                        refMin = row.refMin?.let(BigDecimal::valueOf),
                        refMax = row.refMax?.let(BigDecimal::valueOf),
                    ),
                )
            }
        log.info("Extracted {} results from lab report {}", extraction.results.size, reportId)
    }

    fun recentReports(): List<LabReportDto> =
        reports.findTop20ByOrderByCreatedAtDesc().map { it.toDto(results.findByLabReportIdOrderById(it.id!!).size) }

    fun detail(reportId: Long): LabReportDetail {
        val report = reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        val rows = results.findByLabReportIdOrderById(reportId)
        return LabReportDetail(report.toDto(rows.size), rows.map { it.toDto() })
    }

    @Transactional
    fun review(
        reportId: Long,
        confirm: Boolean,
    ): LabReportDto {
        val report = reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        report.status = if (confirm) "confirmed" else "discarded"
        reports.save(report)
        return report.toDto(results.findByLabReportIdOrderById(reportId).size)
    }

    @Transactional
    fun updateResult(
        resultId: Long,
        update: LabResultUpdate,
    ): LabResultDto {
        val row = results.findById(resultId).orElseThrow { NoSuchElementException("Lab result $resultId not found") }
        // Validate before mutating the managed entity (anything assigned would be flushed).
        val newValue = update.value?.let(BigDecimal::valueOf) ?: row.value
        val newTextValue = if (update.textValue != null) update.textValue.ifBlank { null } else row.textValue
        require(newValue != null || newTextValue != null) { "A result needs a numeric value or a text value" }
        val newAnalyteId =
            update.analyteCode?.let { code ->
                matcher.byCode(code)?.id ?: throw IllegalArgumentException("Unknown analyte code: $code")
            } ?: row.analyteId
        val newDate =
            if (update.date != null) {
                update.date.ifBlank { null }?.let { raw ->
                    runCatching { LocalDate.parse(raw) }
                        .getOrElse { throw IllegalArgumentException("Invalid date: $raw") }
                }
            } else {
                row.resultDate
            }
        row.value = newValue
        row.textValue = newTextValue
        row.analyteId = newAnalyteId
        row.resultDate = newDate
        update.unit?.let { row.unit = it.ifBlank { null } }
        update.reviewed?.let { row.reviewed = it }
        return results.save(row).toDto()
    }

    /** Re-queues a report for extraction; existing results are replaced by the job. */
    @Transactional
    fun prepareReextraction(reportId: Long): LabReportDto {
        val report = reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        require(report.status != "confirmed") { "Confirmed reports cannot be re-extracted" }
        report.status = "pending_review"
        report.extracting = true
        reports.save(report)
        return report.toDto(results.findByLabReportIdOrderById(reportId).size)
    }

    @Transactional
    fun deleteResult(resultId: Long) = results.deleteById(resultId)

    /** Removes the report and all its results (the stored file is kept). */
    @Transactional
    fun deleteReport(reportId: Long) {
        if (!reports.existsById(reportId)) throw NoSuchElementException("Lab report $reportId not found")
        results.deleteByLabReportId(reportId)
        reports.deleteById(reportId)
    }

    private fun LabReportEntity.toDto(resultCount: Int) =
        LabReportDto(
            id = id!!,
            date = date.toString(),
            laboratory = laboratory,
            filename = filename,
            status = status,
            extracting = extracting,
            resultCount = resultCount,
        )

    private fun LabResultEntity.toDto(): LabResultDto {
        val analyte = analyteId?.let { analyteNameAndCode(it) }
        return LabResultDto(
            id = id!!,
            rawName = rawName,
            analyteCode = analyte?.first,
            analyteName = analyte?.second,
            date = resultDate?.toString(),
            value = value?.toDouble(),
            textValue = textValue,
            unit = unit,
            refMin = refMin?.toDouble(),
            refMax = refMax?.toDouble(),
            reviewed = reviewed,
        )
    }

    private fun analyteNameAndCode(analyteId: Long): Pair<String, String>? =
        jdbc
            .query(
                "SELECT code, name FROM analytes WHERE id = ?",
                { rs, _ -> rs.getString("code") to rs.getString("name") },
                analyteId,
            ).firstOrNull()
}
