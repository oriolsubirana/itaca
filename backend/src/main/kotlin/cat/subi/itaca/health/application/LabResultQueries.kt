package cat.subi.itaca.health.application

import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class AnalyteSeriesPoint(
    val date: String,
    val value: Double,
    val refMin: Double?,
    val refMax: Double?,
)

data class AnalyteSeries(
    val code: String,
    val name: String,
    val unit: String,
    val points: List<AnalyteSeriesPoint>,
)

/**
 * A chartable measurement: either a dictionary analyte ("code:...") or an
 * un-normalized result grouped by its raw name ("raw:..."), so any loaded
 * numeric value can be plotted.
 */
data class MeasurementRef(
    val key: String,
    val name: String,
    val unit: String,
    val category: String,
    val normalized: Boolean,
)

/**
 * Read side of the lab results: per-analyte series from CONFIRMED reports
 * only. Exposed to the chat as query_lab_results.
 */
@Service
class LabResultQueries(
    private val matcher: AnalyteMatcher,
    private val jdbc: JdbcTemplate,
) : ChatTools {
    @Tool(
        name = "query_lab_results",
        description =
            "Returns the time series of a lab analyte from confirmed reports (value, unit and reference " +
                "range per date). The analyte can be a Spanish name, a synonym (PCR, VSG...) or a code.",
    )
    fun queryLabResults(
        @ToolParam(description = "Analyte name, synonym or code, e.g. 'calprotectina' or 'PCR'") analyte: String,
    ): AnalyteSeries? = matcher.match(analyte)?.let { series(it) }

    fun seriesByCode(code: String): AnalyteSeries? = matcher.byCode(code)?.let { series(it) }

    /** Analytes that have at least one confirmed result (for the chart selector). */
    fun analytesWithData(): List<AnalyteRef> =
        jdbc.query(
            """
            SELECT DISTINCT a.id, a.code, a.name, a.canonical_unit, a.category
            FROM analytes a
            JOIN lab_results lr ON lr.analyte_id = a.id AND lr.value IS NOT NULL
            JOIN lab_reports r ON r.id = lr.lab_report_id AND r.status = 'confirmed'
            ORDER BY a.category NULLS LAST, a.name
            """.trimIndent(),
        ) { rs, _ ->
            AnalyteRef(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("canonical_unit"),
                rs.getString("category"),
            )
        }

    /** Everything chartable: dictionary analytes plus un-normalized results by raw name. */
    fun chartableMeasurements(): List<MeasurementRef> {
        val dictionary =
            analytesWithData().map {
                MeasurementRef("code:${it.code}", it.name, it.canonicalUnit, it.category ?: "Otros", true)
            }
        val raw =
            jdbc.query(
                """
                SELECT lower(trim(lr.raw_name)) AS k,
                       (array_agg(lr.raw_name ORDER BY r.created_at DESC))[1] AS name,
                       (array_agg(lr.unit ORDER BY r.created_at DESC))[1] AS unit
                FROM lab_results lr JOIN lab_reports r ON r.id = lr.lab_report_id
                WHERE lr.analyte_id IS NULL AND lr.value IS NOT NULL AND r.status = 'confirmed'
                GROUP BY lower(trim(lr.raw_name))
                ORDER BY name
                """.trimIndent(),
            ) { rs, _ ->
                MeasurementRef(
                    key = "raw:${rs.getString("k")}",
                    name = rs.getString("name"),
                    unit = rs.getString("unit") ?: "",
                    category = "Sin normalizar",
                    normalized = false,
                )
            }
        return dictionary + raw
    }

    /** Series for a measurement key ("code:..." dictionary analyte or "raw:..." raw name). */
    fun seriesByKey(key: String): AnalyteSeries? =
        when {
            key.startsWith("code:") -> seriesByCode(key.removePrefix("code:"))
            key.startsWith("raw:") -> rawSeries(key.removePrefix("raw:"))
            else -> null
        }

    private fun rawSeries(rawKey: String): AnalyteSeries? {
        val points =
            jdbc.query(
                """
                SELECT DISTINCT ON (COALESCE(lr.result_date, r.date))
                       COALESCE(lr.result_date, r.date) AS d, lr.value, lr.ref_min, lr.ref_max
                FROM lab_results lr JOIN lab_reports r ON r.id = lr.lab_report_id
                WHERE lower(trim(lr.raw_name)) = ? AND lr.value IS NOT NULL
                      AND lr.analyte_id IS NULL AND r.status = 'confirmed'
                ORDER BY COALESCE(lr.result_date, r.date), r.created_at DESC
                """.trimIndent(),
                { rs, _ ->
                    AnalyteSeriesPoint(
                        date = rs.getDate("d").toLocalDate().toString(),
                        value = rs.getDouble("value"),
                        refMin = rs.getBigDecimal("ref_min")?.toDouble(),
                        refMax = rs.getBigDecimal("ref_max")?.toDouble(),
                    )
                },
                rawKey,
            )
        if (points.isEmpty()) return null
        val meta =
            jdbc
                .query(
                    """
                    SELECT (array_agg(lr.raw_name ORDER BY r.created_at DESC))[1] AS name,
                           (array_agg(lr.unit ORDER BY r.created_at DESC))[1] AS unit
                    FROM lab_results lr JOIN lab_reports r ON r.id = lr.lab_report_id
                    WHERE lower(trim(lr.raw_name)) = ? AND lr.analyte_id IS NULL AND r.status = 'confirmed'
                    """.trimIndent(),
                    { rs, _ -> rs.getString("name") to (rs.getString("unit") ?: "") },
                    rawKey,
                ).firstOrNull() ?: return null
        return AnalyteSeries(code = "raw:$rawKey", name = meta.first, unit = meta.second, points = points)
    }

    private fun series(analyte: AnalyteRef): AnalyteSeries =
        AnalyteSeries(
            code = analyte.code,
            name = analyte.name,
            unit = analyte.canonicalUnit,
            points =
                jdbc.query(
                    // Effective date = the result's own date (cumulative reports) or the
                    // report date. One point per effective date: duplicate uploads collapse,
                    // most recent winning, while a cumulative report's distinct dates stay.
                    """
                    SELECT DISTINCT ON (COALESCE(lr.result_date, r.date))
                           COALESCE(lr.result_date, r.date) AS d, lr.value, lr.ref_min, lr.ref_max
                    FROM lab_results lr JOIN lab_reports r ON r.id = lr.lab_report_id
                    WHERE lr.analyte_id = ? AND lr.value IS NOT NULL AND r.status = 'confirmed'
                    ORDER BY COALESCE(lr.result_date, r.date), r.created_at DESC
                    """.trimIndent(),
                    { rs, _ ->
                        AnalyteSeriesPoint(
                            date = rs.getDate("d").toLocalDate().toString(),
                            value = rs.getDouble("value"),
                            refMin = rs.getBigDecimal("ref_min")?.toDouble(),
                            refMax = rs.getBigDecimal("ref_max")?.toDouble(),
                        )
                    },
                    analyte.id,
                ),
        )
}
