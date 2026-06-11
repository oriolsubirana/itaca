package cat.subi.itaca.health.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

data class AnalyteRef(
    val id: Long,
    val code: String,
    val name: String,
    val canonicalUnit: String,
)

/**
 * Normalizes raw analyte names from lab reports against the seeded dictionary
 * (canonical Spanish name + Spanish/Swiss lab synonyms), case-insensitively.
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
