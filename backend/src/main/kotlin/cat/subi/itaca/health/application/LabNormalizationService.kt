package cat.subi.itaca.health.application

import cat.subi.itaca.health.adapter.out.persistence.LabReportRepository
import cat.subi.itaca.health.adapter.out.persistence.LabResultEntity
import cat.subi.itaca.health.adapter.out.persistence.LabResultRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Re-runs only the dictionary matching over already-stored lab results (no AI call),
 * so reports confirmed before new synonyms were added pick them up. Cheap and safe.
 */
@Service
class LabNormalizationService(
    private val reports: LabReportRepository,
    private val results: LabResultRepository,
    private val matcher: AnalyteMatcher,
) {
    @Transactional
    fun renormalize(reportId: Long): RenormalizeResult {
        reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        return renormalizeRows(results.findByLabReportIdOrderById(reportId))
    }

    @Transactional
    fun renormalizeAll(): RenormalizeResult = renormalizeRows(results.findAll())

    private fun renormalizeRows(rows: List<LabResultEntity>): RenormalizeResult {
        var changed = 0
        rows.forEach { row ->
            if (normalizeRow(row)) {
                results.save(row)
                changed++
            }
        }
        return RenormalizeResult(changed, rows.size)
    }

    /** Recomputes a row's analyte link with the current dictionary; returns true if it changed. */
    private fun normalizeRow(row: LabResultEntity): Boolean {
        val newId =
            if (row.value != null) {
                matcher.match(row.rawName)?.takeIf { unitsCompatible(row.unit, it.canonicalUnit) }?.id
            } else {
                null
            }
        if (row.analyteId == newId) return false
        row.analyteId = newId
        return true
    }
}
