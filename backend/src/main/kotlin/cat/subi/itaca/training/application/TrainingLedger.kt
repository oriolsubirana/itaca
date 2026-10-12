package cat.subi.itaca.training.application

import cat.subi.itaca.training.domain.DoneActivity
import cat.subi.itaca.training.domain.Sport
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

/** One imported Strava activity reduced to what the plan and load models need. */
data class LedgerActivity(
    val date: LocalDate,
    /** Null for activities outside the plan's sports (hike, yoga...). */
    val sport: Sport?,
    val minutes: Int,
    val avgHr: Double?,
)

/**
 * Read side shared by the plan-compliance and load views: what was actually trained, from the
 * Strava activities (swim arrives as type 'other' with a Swim sport) and the logged gym workouts.
 * Dates are the local (Europe/Madrid) day of the activity.
 */
@Component
class TrainingLedger(
    private val jdbc: JdbcTemplate,
) {
    fun activities(
        from: LocalDate,
        to: LocalDate,
    ): List<LedgerActivity> =
        jdbc.query(
            """
            SELECT (start_date AT TIME ZONE 'Europe/Madrid')::date AS day, type, sport, moving_time_s, avg_hr
            FROM activities
            WHERE (start_date AT TIME ZONE 'Europe/Madrid')::date BETWEEN ? AND ?
            ORDER BY start_date
            """.trimIndent(),
            { rs, _ ->
                LedgerActivity(
                    date = rs.getDate("day").toLocalDate(),
                    sport = sportOf(rs.getString("type"), rs.getString("sport")),
                    minutes = rs.getInt("moving_time_s") / SECONDS_PER_MINUTE,
                    avgHr = (rs.getObject("avg_hr") as? Number)?.toDouble(),
                )
            },
            from,
            to,
        )

    /** Everything trained in the range, plan-shaped: Strava sessions plus logged gym workouts. */
    fun done(
        from: LocalDate,
        to: LocalDate,
    ): List<DoneActivity> {
        val strava = activities(from, to).mapNotNull { a -> a.sport?.let { DoneActivity(a.date, it, a.minutes) } }
        val gym =
            jdbc.query(
                """
                SELECT DISTINCT w.date FROM workouts w
                WHERE w.date BETWEEN ? AND ?
                  AND (w.completed OR EXISTS (SELECT 1 FROM sets s WHERE s.workout_id = w.id))
                """.trimIndent(),
                { rs, _ -> DoneActivity(rs.getDate("date").toLocalDate(), Sport.GYM, 0) },
                from,
                to,
            )
        return strava + gym
    }

    /** Average kcal per minute per sport over the last half year (Strava calories), for estimates. */
    fun kcalPerMinute(today: LocalDate): Map<Sport, Double> =
        jdbc
            .query(
                """
                SELECT type, sport, SUM(calories)::float8 AS kcal, SUM(moving_time_s)::float8 / 60 AS minutes
                FROM activities
                WHERE calories > 0 AND moving_time_s > 0 AND start_date >= ?
                GROUP BY type, sport
                """.trimIndent(),
                { rs, _ ->
                    Triple(
                        sportOf(rs.getString("type"), rs.getString("sport")),
                        rs.getDouble("kcal"),
                        rs.getDouble("minutes"),
                    )
                },
                today.minusDays(HISTORY_DAYS).atStartOfDay(),
            ).filter { it.first != null }
            .groupBy({ it.first!! }, { it.second to it.third })
            .mapValues { (_, rows) -> rows.sumOf { it.first } / rows.sumOf { it.second } }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val HISTORY_DAYS = 180L

        fun sportOf(
            type: String,
            sport: String?,
        ): Sport? =
            when (type) {
                "run" -> Sport.RUN
                "bike" -> Sport.BIKE
                "gym" -> Sport.GYM
                "other" -> if (sport?.lowercase()?.startsWith("swim") == true) Sport.SWIM else null
                else -> null
            }
    }
}
