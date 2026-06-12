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

private val ACCENTS =
    mapOf(
        'à' to 'a',
        'á' to 'a',
        'â' to 'a',
        'ä' to 'a',
        'ã' to 'a',
        'è' to 'e',
        'é' to 'e',
        'ê' to 'e',
        'ë' to 'e',
        'ì' to 'i',
        'í' to 'i',
        'î' to 'i',
        'ï' to 'i',
        'ò' to 'o',
        'ó' to 'o',
        'ô' to 'o',
        'ö' to 'o',
        'õ' to 'o',
        'ù' to 'u',
        'ú' to 'u',
        'û' to 'u',
        'ü' to 'u',
        'ç' to 'c',
        'ñ' to 'n',
    )

// Leading specimen code (S-Glucose, B-Leukocytes) and Catalan "San-/sang" = "en sang".
// A single-letter code only strips when a letter follows, so "S-100" (protein) is left intact.
private val SAMPLE_PREFIX = Regex("^((san|sang|sangre|lcr)[-\\s]+|[sbpu]-(?=[a-z]))")

// Decoration that marks specimen or method, not a different analyte — safe to drop before matching.
// NOT stripped: "volum(en)" and "%" — those ARE different measurements (corpuscular volume, relative count).
private val DECORATION =
    listOf(
        Regex("\\(automatitzat\\)"),
        Regex("\\(automatizado\\)"),
        Regex("\\(automated\\)"),
        Regex("\\(v\\.? ?absolut[o]?\\)"),
        Regex("\\(valor absolut[o]?\\)"),
        Regex("\\babs\\.?"),
        Regex(",? ?en sang(re)?\\b"),
        Regex(",? ?en serum\\b"),
        Regex(",? ?en seric[oa]\\b"),
        Regex(",? ?en orina\\b"),
        Regex(",? ?en plasma\\b"),
    )

private val PAREN = Regex("\\(([^)]+)\\)")

private fun fold(s: String): String = buildString { for (c in s.lowercase()) append(ACCENTS[c] ?: c) }

/** Reduce to alphanumerics + single spaces, for exact comparison on a canonical form. */
private fun canon(s: String): String = s.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * Normalizes raw analyte names from lab reports against the seeded dictionary
 * (canonical Spanish name + Spanish/Catalan/English/German/French synonyms).
 * Matching is exact on a canonical form (accent-folded, decoration-stripped),
 * so it tolerates wording variants without merging genuinely different measurements.
 */
@Component
class AnalyteMatcher(
    private val jdbc: JdbcTemplate,
) {
    // The dictionary is seeded by Liquibase at startup and static at runtime, so the
    // canonical-key index is built once and reused — match() is called per result row
    // (a full table scan + map build per call would make renormalizeAll O(rows × analytes)).
    private val index: Map<String, AnalyteRef> by lazy { keyIndex() }

    fun match(rawName: String): AnalyteRef? {
        if (rawName.isBlank()) return null
        return candidates(rawName).firstNotNullOfOrNull { index[it] }
    }

    /** Canonical candidate keys for a raw name, in priority order. */
    private fun candidates(raw: String): List<String> {
        val folded = fold(raw.trim())
        val keys = LinkedHashSet<String>()
        keys += canon(folded)
        val noPrefix = folded.replace(SAMPLE_PREFIX, "")
        keys += canon(noPrefix)
        var stripped = noPrefix
        DECORATION.forEach { stripped = it.replace(stripped, " ") }
        keys += canon(stripped)
        // A parenthetical code is a strong signal: "Aspartat aminotransferasa (AST)" -> AST.
        PAREN.findAll(folded).forEach { m ->
            m.groupValues[1].split("/").forEach { keys += canon(it) }
        }
        return keys.filter { it.isNotBlank() }
    }

    /** All dictionary entries indexed by every canonical key (name, code, synonyms). */
    private fun keyIndex(): Map<String, AnalyteRef> {
        val index = HashMap<String, AnalyteRef>()
        jdbc.query("SELECT id, code, name, canonical_unit, category, synonyms FROM analytes") { rs, _ ->
            val ref =
                AnalyteRef(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("canonical_unit"),
                    rs.getString("category"),
                )
            val synonyms = (rs.getArray("synonyms")?.array as? Array<*>)?.filterIsInstance<String>().orEmpty()
            (synonyms + ref.name + ref.code).forEach { key ->
                index.putIfAbsent(canon(fold(key)), ref)
            }
        }
        return index
    }

    fun byCode(code: String): AnalyteRef? =
        jdbc
            .query(
                "SELECT id, code, name, canonical_unit, category FROM analytes WHERE code = ?",
                { rs, _ ->
                    AnalyteRef(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("canonical_unit"),
                        rs.getString("category"),
                    )
                },
                code,
            ).firstOrNull()
}
