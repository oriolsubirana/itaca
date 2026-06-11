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
            SELECT DISTINCT a.id, a.code, a.name, a.canonical_unit
            FROM analytes a
            JOIN lab_results lr ON lr.analyte_id = a.id
            JOIN lab_reports r ON r.id = lr.lab_report_id AND r.status = 'confirmed'
            ORDER BY a.name
            """.trimIndent(),
        ) { rs, _ ->
            AnalyteRef(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("canonical_unit"),
            )
        }

    private fun series(analyte: AnalyteRef): AnalyteSeries =
        AnalyteSeries(
            code = analyte.code,
            name = analyte.name,
            unit = analyte.canonicalUnit,
            points =
                jdbc.query(
                    """
                    SELECT r.date, lr.value, lr.ref_min, lr.ref_max
                    FROM lab_results lr JOIN lab_reports r ON r.id = lr.lab_report_id
                    WHERE lr.analyte_id = ? AND r.status = 'confirmed'
                    ORDER BY r.date
                    """.trimIndent(),
                    { rs, _ ->
                        AnalyteSeriesPoint(
                            date = rs.getDate("date").toLocalDate().toString(),
                            value = rs.getDouble("value"),
                            refMin = rs.getBigDecimal("ref_min")?.toDouble(),
                            refMax = rs.getBigDecimal("ref_max")?.toDouble(),
                        )
                    },
                    analyte.id,
                ),
        )
}
