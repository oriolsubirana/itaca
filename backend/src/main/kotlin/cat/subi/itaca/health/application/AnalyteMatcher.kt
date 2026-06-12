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

/**
 * Conservative unit guard for normalization. The dictionary holds concentration /
 * activity blood markers; a numeric result that shares an analyte name but is a
 * per-visual-field microscopy count (urine sediment: "/Gesichtsfeld", "/HPF") is a
 * different measurement and must not feed that analyte's series. Only this clear
 * cross-family mismatch blocks a match — equivalent concentration spellings
 * (10^3/µL ≡ 10^9/L) are never subdivided, and a blank/unknown unit never blocks.
 */
fun unitsCompatible(
    resultUnit: String?,
    canonicalUnit: String,
): Boolean {
    val u = resultUnit?.trim().orEmpty()
    if (u.isEmpty()) return true
    return PER_FIELD_UNIT.containsMatchIn(u) == PER_FIELD_UNIT.containsMatchIn(canonicalUnit)
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
