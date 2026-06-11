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
    val status: String,
    val resultCount: Int,
)

data class LabResultDto(
    val id: Long,
    val rawName: String,
    val analyteCode: String?,
    val analyteName: String?,
    val value: Double,
    val unit: String?,
    val refMin: Double?,
    val refMax: Double?,
)

data class LabReportDetail(
    val report: LabReportDto,
    val results: List<LabResultDto>,
)

data class LabResultUpdate(
    val value: Double? = null,
    val unit: String? = null,
    val analyteCode: String? = null,
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
        val report = reports.save(LabReportEntity(date = LocalDate.now(), storagePath = path))
        return report.toDto(0)
    }

    /** Executed by the JobRunr handler, with retries on failure. */
    @Transactional
    fun runExtraction(reportId: Long) {
        val report = reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        val pdf = storage.load(checkNotNull(report.storagePath) { "Report $reportId has no stored file" })
        val extraction = extractor.extract(pdf)

        extraction.date?.let { runCatching { report.date = LocalDate.parse(it) } }
        extraction.laboratory?.takeIf { it.isNotBlank() }?.let { report.laboratory = it }
        reports.save(report)

        results.deleteByLabReportId(reportId) // idempotent re-runs (JobRunr retries)
        extraction.results
            .filter { it.analyte.isNotBlank() && it.value != null }
            .forEach { row ->
                results.save(
                    LabResultEntity(
                        labReportId = reportId,
                        analyteId = matcher.match(row.analyte)?.id,
                        rawName = row.analyte,
                        value = BigDecimal.valueOf(row.value!!),
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
        update.value?.let { row.value = BigDecimal.valueOf(it) }
        update.unit?.let { row.unit = it }
        update.analyteCode?.let { code ->
            row.analyteId = matcher.byCode(code)?.id ?: throw IllegalArgumentException("Unknown analyte code: $code")
        }
        return results.save(row).toDto()
    }

    @Transactional
    fun deleteResult(resultId: Long) = results.deleteById(resultId)

    private fun LabReportEntity.toDto(resultCount: Int) =
        LabReportDto(
            id = id!!,
            date = date.toString(),
            laboratory = laboratory,
            status = status,
            resultCount = resultCount,
        )

    private fun LabResultEntity.toDto(): LabResultDto {
        val analyte = analyteId?.let { analyteNameAndCode(it) }
        return LabResultDto(
            id = id!!,
            rawName = rawName,
            analyteCode = analyte?.first,
            analyteName = analyte?.second,
            value = value.toDouble(),
            unit = unit,
            refMin = refMin?.toDouble(),
            refMax = refMax?.toDouble(),
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
