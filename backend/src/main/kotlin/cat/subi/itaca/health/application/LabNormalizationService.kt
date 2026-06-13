package cat.subi.itaca.health.application

import cat.subi.itaca.health.adapter.out.anthropic.NameToMap
import cat.subi.itaca.health.adapter.out.anthropic.SemanticAnalyteMatcher
import cat.subi.itaca.health.adapter.out.persistence.LabReportRepository
import cat.subi.itaca.health.adapter.out.persistence.LabResultEntity
import cat.subi.itaca.health.adapter.out.persistence.LabResultRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Re-runs only the dictionary matching over already-stored lab results (no AI call),
 * so reports confirmed before new synonyms were added pick them up. Cheap and safe.
 * The AI variant adds a semantic pass for the multilingual long tail.
 */
@Service
class LabNormalizationService(
    private val reports: LabReportRepository,
    private val results: LabResultRepository,
    private val matcher: AnalyteMatcher,
    private val semantic: SemanticAnalyteMatcher,
) {
    @Transactional
    fun renormalize(reportId: Long): RenormalizeResult {
        reports.findById(reportId).orElseThrow { NoSuchElementException("Lab report $reportId not found") }
        return renormalizeRows(results.findByLabReportIdOrderById(reportId))
    }

    @Transactional
    fun renormalizeAll(): RenormalizeResult = renormalizeRows(results.findAll())

    /**
     * Cheap deterministic pass, then ask the model to map whatever is still un-normalized
     * to canonical codes (any language). One AI call for all distinct leftover names.
     */
    @Transactional
    fun semanticRenormalize(): RenormalizeResult {
        val rows = results.findAll()
        var changed = renormalizeRows(rows).changed

        val unmatched = rows.filter { it.analyteId == null && it.value != null }
        val distinct = unmatched.distinctBy { it.rawName.trim().lowercase() }.map { NameToMap(it.rawName, it.unit) }
        val mapping = semantic.mapToCodes(distinct)
        unmatched.forEach { row ->
            val code = mapping[row.rawName.trim().lowercase()] ?: return@forEach
            val ref = matcher.byCode(code) ?: return@forEach
            if (!unitsCompatible(row.unit, ref.canonicalUnit)) return@forEach
            row.analyteId = ref.id
            results.save(row)
            changed++
        }
        return RenormalizeResult(changed, rows.size)
    }

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
