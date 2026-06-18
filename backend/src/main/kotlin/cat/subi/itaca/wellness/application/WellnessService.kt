package cat.subi.itaca.wellness.application

import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** One day of Garmin wellness metrics (any field may be missing). */
data class WellnessDayDto(
    val date: String,
    val sleepMinutes: Int?,
    val deepMinutes: Int?,
    val lightMinutes: Int?,
    val remMinutes: Int?,
    val awakeMinutes: Int?,
    val sleepScore: Int?,
    val hrvAvgMs: Int?,
    val hrvStatus: String?,
    val restingHr: Int?,
    val stressAvg: Int?,
    val bodyBatteryHigh: Int?,
    val bodyBatteryLow: Int?,
    val steps: Int?,
    val activeCalories: Int?,
    val spo2Avg: Int?,
    val respirationAvg: Double?,
)

/** Recent wellness days plus simple averages over the window. */
data class WellnessSummary(
    val days: List<WellnessDayDto>,
    val avgSleepMinutes: Int?,
    val avgHrvMs: Int?,
    val avgRestingHr: Int?,
)

/** A day's metrics to upsert (idempotent on the date; the Garmin sync re-sends freely). */
data class WellnessCommand(
    val date: LocalDate,
    val sleepMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepScore: Int? = null,
    val hrvAvgMs: Int? = null,
    val hrvStatus: String? = null,
    val restingHr: Int? = null,
    val stressAvg: Int? = null,
    val bodyBatteryHigh: Int? = null,
    val bodyBatteryLow: Int? = null,
    val steps: Int? = null,
    val activeCalories: Int? = null,
    val spo2Avg: Int? = null,
    val respirationAvg: Double? = null,
)

/**
 * Application service of the wellness context: stores the daily Garmin metrics pushed by the
 * external sync (POST /api/wellness/daily) and exposes them to the chat (read-only). Records and
 * describes data only — the health rule holds: no medical interpretation.
 */
@Service
class WellnessService(
    private val jdbc: JdbcTemplate,
) : ChatTools {
    @Transactional
    fun upsert(c: WellnessCommand): WellnessDayDto {
        jdbc.update(
            """
            INSERT INTO daily_wellness (
                date, sleep_minutes, deep_minutes, light_minutes, rem_minutes, awake_minutes, sleep_score,
                hrv_avg_ms, hrv_status, resting_hr, stress_avg, body_battery_high, body_battery_low,
                steps, active_calories, spo2_avg, respiration_avg
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (date) DO UPDATE SET
                sleep_minutes = EXCLUDED.sleep_minutes, deep_minutes = EXCLUDED.deep_minutes,
                light_minutes = EXCLUDED.light_minutes, rem_minutes = EXCLUDED.rem_minutes,
                awake_minutes = EXCLUDED.awake_minutes, sleep_score = EXCLUDED.sleep_score,
                hrv_avg_ms = EXCLUDED.hrv_avg_ms, hrv_status = EXCLUDED.hrv_status,
                resting_hr = EXCLUDED.resting_hr, stress_avg = EXCLUDED.stress_avg,
                body_battery_high = EXCLUDED.body_battery_high, body_battery_low = EXCLUDED.body_battery_low,
                steps = EXCLUDED.steps, active_calories = EXCLUDED.active_calories,
                spo2_avg = EXCLUDED.spo2_avg, respiration_avg = EXCLUDED.respiration_avg, updated_at = now()
            """.trimIndent(),
            java.sql.Date.valueOf(c.date),
            c.sleepMinutes,
            c.deepMinutes,
            c.lightMinutes,
            c.remMinutes,
            c.awakeMinutes,
            c.sleepScore,
            c.hrvAvgMs,
            c.hrvStatus,
            c.restingHr,
            c.stressAvg,
            c.bodyBatteryHigh,
            c.bodyBatteryLow,
            c.steps,
            c.activeCalories,
            c.spo2Avg,
            c.respirationAvg,
        )
        return jdbc.query("SELECT * FROM daily_wellness WHERE date = ?", MAPPER, java.sql.Date.valueOf(c.date)).single()
    }

    fun recent(days: Int): List<WellnessDayDto> =
        jdbc.query(
            "SELECT * FROM daily_wellness WHERE date >= ? ORDER BY date DESC",
            MAPPER,
            java.sql.Date.valueOf(LocalDate.now().minusDays(days.toLong())),
        )

    @Tool(
        name = "query_wellness",
        description =
            "Returns Garmin wellness metrics for the last days (default 7): sleep (minutes, stages, score), " +
                "overnight HRV (ms + status), resting heart rate, stress, body battery, steps, SpO2 and " +
                "respiration, plus simple averages. Use it for sleep, HRV, recovery or readiness questions.",
    )
    fun queryWellness(
        @ToolParam(description = "How many days back to look (default 7)", required = false) days: Int?,
    ): WellnessSummary {
        val rows = recent(days ?: DEFAULT_DAYS)
        return WellnessSummary(
            days = rows,
            avgSleepMinutes = rows.mapNotNull { it.sleepMinutes }.averageOrNull(),
            avgHrvMs = rows.mapNotNull { it.hrvAvgMs }.averageOrNull(),
            avgRestingHr = rows.mapNotNull { it.restingHr }.averageOrNull(),
        )
    }

    private fun List<Int>.averageOrNull(): Int? = if (isEmpty()) null else average().toInt()

    private companion object {
        const val DEFAULT_DAYS = 7
        val MAPPER =
            RowMapper { rs, _ ->
                fun int(c: String) = (rs.getObject(c) as? Number)?.toInt()
                WellnessDayDto(
                    date = rs.getDate("date").toLocalDate().toString(),
                    sleepMinutes = int("sleep_minutes"),
                    deepMinutes = int("deep_minutes"),
                    lightMinutes = int("light_minutes"),
                    remMinutes = int("rem_minutes"),
                    awakeMinutes = int("awake_minutes"),
                    sleepScore = int("sleep_score"),
                    hrvAvgMs = int("hrv_avg_ms"),
                    hrvStatus = rs.getString("hrv_status"),
                    restingHr = int("resting_hr"),
                    stressAvg = int("stress_avg"),
                    bodyBatteryHigh = int("body_battery_high"),
                    bodyBatteryLow = int("body_battery_low"),
                    steps = int("steps"),
                    activeCalories = int("active_calories"),
                    spo2Avg = int("spo2_avg"),
                    respirationAvg = (rs.getObject("respiration_avg") as? Number)?.toDouble(),
                )
            }
    }
}
