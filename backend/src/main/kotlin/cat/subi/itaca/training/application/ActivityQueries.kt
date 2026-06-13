package cat.subi.itaca.training.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class ActivityDto(
    val id: Long,
    val type: String,
    val sport: String?,
    val date: String,
    val name: String?,
    val distanceKm: Double?,
    val durationS: Int?,
    val elevationM: Double?,
    val avgHr: Double?,
    val avgSpeedKmh: Double?,
)

data class BikeWeek(
    val label: String,
    val km: Double,
)

data class ActivitiesView(
    val connected: Boolean,
    val activities: List<ActivityDto>,
    val weekBikeKm: Double,
    val weekRunKm: Double,
    val weekHikes: Int,
    val weekMovingTimeS: Int,
    val bikeWeekly: List<BikeWeek>,
)

private val MES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Read side of the Strava activities, for the Gym/Entreno dashboard feed. */
@Service
class ActivityQueries(
    private val jdbc: JdbcTemplate,
) {
    fun view(): ActivitiesView {
        val connected = jdbc.queryForObject("SELECT count(*) FROM strava_account", Int::class.java)!! > 0
        return ActivitiesView(connected, recent(), weekBike(), weekRun(), weekHikes(), weekMoving(), bikeWeekly())
    }

    private fun recent(): List<ActivityDto> =
        jdbc.query(
            """
            SELECT id, type, sport, name, start_date, distance_m, moving_time_s, elevation_m, avg_hr, avg_speed_ms
            FROM activities ORDER BY start_date DESC LIMIT 20
            """.trimIndent(),
        ) { rs, _ ->
            ActivityDto(
                id = rs.getLong("id"),
                type = rs.getString("type"),
                sport = rs.getString("sport"),
                date = isoDate(rs.getTimestamp("start_date")),
                name = rs.getString("name"),
                distanceKm = rs.getBigDecimal("distance_m")?.toDouble()?.div(METERS_PER_KM),
                durationS = rs.getObject("moving_time_s") as? Int,
                elevationM = rs.getBigDecimal("elevation_m")?.toDouble(),
                avgHr = rs.getBigDecimal("avg_hr")?.toDouble(),
                avgSpeedKmh = rs.getBigDecimal("avg_speed_ms")?.toDouble()?.times(MS_TO_KMH),
            )
        }

    private fun weekBike(): Double = weekSum("bike")

    private fun weekRun(): Double = weekSum("run")

    private fun weekSum(type: String): Double =
        jdbc.queryForObject(
            """
            SELECT COALESCE(sum(distance_m), 0) / 1000.0 FROM activities
            WHERE type = ? AND start_date >= date_trunc('week', now())
            """.trimIndent(),
            Double::class.java,
            type,
        )!!

    private fun weekHikes(): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM activities WHERE type = 'hike' AND start_date >= date_trunc('week', now())",
            Int::class.java,
        )!!

    private fun weekMoving(): Int =
        jdbc.queryForObject(
            "SELECT COALESCE(sum(moving_time_s), 0) FROM activities WHERE start_date >= date_trunc('week', now())",
            Int::class.java,
        )!!

    private fun bikeWeekly(): List<BikeWeek> =
        jdbc.query(
            """
            SELECT g.wk, COALESCE(sum(a.distance_m) / 1000.0, 0) AS km
            FROM generate_series(
                date_trunc('week', now()) - interval '7 weeks', date_trunc('week', now()), interval '1 week'
            ) g(wk)
            LEFT JOIN activities a ON date_trunc('week', a.start_date) = g.wk AND a.type = 'bike'
            GROUP BY g.wk ORDER BY g.wk
            """.trimIndent(),
        ) { rs, _ ->
            val d = rs.getTimestamp("wk").toLocalDateTime().toLocalDate()
            BikeWeek("${d.dayOfMonth} ${MES[d.monthValue - 1]}", rs.getDouble("km"))
        }

    private fun isoDate(ts: java.sql.Timestamp): String = ts.toLocalDateTime().toLocalDate().toString()

    private companion object {
        const val METERS_PER_KM = 1000.0
        const val MS_TO_KMH = 3.6
    }
}
