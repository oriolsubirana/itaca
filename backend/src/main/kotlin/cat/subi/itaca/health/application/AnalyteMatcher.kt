package cat.subi.itaca.health.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

data class AnalyteRef(
    val id: Long,
    val code: String,
    val name: String,
    val canonicalUnit: String,
    val category: String? = null,
)

private val PER_FIELD_UNIT = Regex("gesic|hpf|/gf|/feld", RegexOption.IGNORE_CASE)

// Sample-type prefixes labs prepend to an analyte name (e.g. Parc Taulí's "San-" =
// "en sang"). They mark the specimen, not a different analyte, so strip them before
// matching: "San-Leucòcits" -> "Leucòcits".
private val SAMPLE_PREFIX = Regex("^(san|sang|sangre|s|b|p|u|lcr)[-\\s]+", RegexOption.IGNORE_CASE)

private val SAME_UNIT_RATIO = Regex("^(\\w+)/\\1$", RegexOption.IGNORE_CASE)
private val CONCENTRATION_UNIT = Regex("10\\^|10\\*|/l|/dl|/ml|/µl|/ul|/nl|/mm|mol/|g/l|u/l", RegexOption.IGNORE_CASE)

private fun isPercent(unit: String) = unit == "%" || unit.contains("percent", ignoreCase = true)

/** Per-volume concentration (10^9/L, 10^3/µL, mg/dL...) but not a dimensionless ratio like L/L. */
private fun isConcentration(unit: String) = !SAME_UNIT_RATIO.matches(unit) && CONCENTRATION_UNIT.containsMatchIn(unit)

/**
 * Conservative unit guard for normalization. The dictionary holds concentration /
 * activity blood markers; a numeric result that shares an analyte name but is a
 * per-visual-field microscopy count (urine sediment) or a relative percentage where
 * the analyte is an absolute count is a different measurement and must not feed that
 * analyte's series. Only these clear cross-family mismatches block a match — equivalent
 * concentration spellings (10^3/µL ≡ 10^9/L) and dimensionless ratios (L/L ≡ %) are
 * never subdivided, and a blank/unknown unit never blocks.
 */
fun unitsCompatible(
    resultUnit: String?,
    canonicalUnit: String,
): Boolean {
    val u = resultUnit?.trim().orEmpty()
    if (u.isEmpty()) return true
    val perFieldOk = PER_FIELD_UNIT.containsMatchIn(u) == PER_FIELD_UNIT.containsMatchIn(canonicalUnit)
    val percentVsAbsolute =
        (isPercent(u) && isConcentration(canonicalUnit)) || (isPercent(canonicalUnit) && isConcentration(u))
    return perFieldOk && !percentVsAbsolute
}

/**
 * Normalizes raw analyte names from lab reports against the seeded dictionary
 * (canonical Spanish name + Spanish/Catalan/English/German/French synonyms),
 * case-insensitively.
 */
@Component
class AnalyteMatcher(
    private val jdbc: JdbcTemplate,
) {
    fun match(rawName: String): AnalyteRef? {
        val needle = rawName.trim()
        if (needle.isEmpty()) return null
        // Exact match first; then retry once with the sample-type prefix stripped.
        return exactMatch(needle) ?: SAMPLE_PREFIX.find(needle)?.let { exactMatch(needle.removeRange(it.range)) }
    }

    private fun exactMatch(needle: String): AnalyteRef? {
        if (needle.isBlank()) return null
        return jdbc
            .query(
                """
                SELECT id, code, name, canonical_unit FROM analytes
                WHERE lower(name) = lower(?)
                   OR lower(code) = lower(?)
                   OR EXISTS (SELECT 1 FROM unnest(synonyms) syn WHERE lower(syn) = lower(?))
                LIMIT 1
                """.trimIndent(),
                { rs, _ ->
                    AnalyteRef(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("canonical_unit"),
                    )
                },
                needle,
                needle,
                needle,
            ).firstOrNull()
    }

    fun byCode(code: String): AnalyteRef? =
        jdbc
            .query(
                "SELECT id, code, name, canonical_unit FROM analytes WHERE code = ?",
                { rs, _ ->
                    AnalyteRef(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("canonical_unit"),
                    )
                },
                code,
            ).firstOrNull()
}
