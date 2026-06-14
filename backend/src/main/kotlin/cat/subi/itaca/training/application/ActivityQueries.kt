package cat.subi.itaca.training.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.roundToLong

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
    val calories: Int?,
    // Routine name of the linked strength workout (gym activities only), when present.
    val routine: String? = null,
)

data class VolumeWeek(
    val label: String,
    val value: Double,
    val sub: String,
)

data class YtdTotals(
    val distance: String?,
    val elevation: String?,
    val time: String,
)

data class SportVolume(
    val unit: String,
    val ytd: YtdTotals,
    val weeks: List<VolumeWeek>,
)

data class ActivitiesView(
    val connected: Boolean,
    val activities: List<ActivityDto>,
    val weekBikeKm: Double,
    val weekRunKm: Double,
    val weekHikes: Int,
    val weekMovingTimeS: Int,
    // Per-sport weekly volume (bike/run/hike in km, gym in hours) for the clickable bars + YTD.
    val volume: Map<String, SportVolume>,
)

private val MES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Read side of the Strava activities, for the Gym/Entreno dashboard feed. */
@Service
class ActivityQueries(
    private val jdbc: JdbcTemplate,
) {
    fun view(): ActivitiesView {
        val connected = jdbc.queryForObject("SELECT count(*) FROM strava_account", Int::class.java)!! > 0
        return ActivitiesView(connected, recent(), weekBike(), weekRun(), weekHikes(), weekMoving(), volume())
    }

    private fun recent(): List<ActivityDto> =
        jdbc.query(
            """
            SELECT a.id, a.type, a.sport, a.name, a.start_date, a.distance_m, a.moving_time_s,
                   a.elevation_m, a.avg_hr, a.avg_speed_ms, a.calories, r.name AS routine
            FROM activities a
            LEFT JOIN workouts w ON w.strava_id = a.strava_id
            LEFT JOIN routines r ON r.id = w.routine_id
            ORDER BY a.start_date DESC LIMIT 20
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
                calories = rs.getObject("calories") as? Int,
                routine = rs.getString("routine"),
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

    private fun volume(): Map<String, SportVolume> = VOLUME_SPORTS.associateWith { sportVolume(it) }

    private fun sportVolume(type: String): SportVolume {
        val hours = type == GYM
        return SportVolume(if (hours) "h" else "km", ytd(type), weeksFor(type, hours))
    }

    private fun weeksFor(
        type: String,
        hours: Boolean,
    ): List<VolumeWeek> {
        val rows =
            jdbc.query(
                """
                SELECT g.wk,
                       count(a.id) AS n,
                       COALESCE(sum(a.distance_m), 0) AS dist_m,
                       COALESCE(sum(a.elevation_m), 0) AS elev_m,
                       COALESCE(sum(a.moving_time_s), 0) AS secs
                FROM generate_series(
                    date_trunc('week', now()) - interval '7 weeks', date_trunc('week', now()), interval '1 week'
                ) g(wk)
                LEFT JOIN activities a ON date_trunc('week', a.start_date) = g.wk AND a.type = ?
                GROUP BY g.wk ORDER BY g.wk
                """.trimIndent(),
                { rs, _ ->
                    WeekAgg(
                        date = rs.getTimestamp("wk").toLocalDateTime().toLocalDate(),
                        n = rs.getInt("n"),
                        km = rs.getBigDecimal("dist_m").toDouble() / METERS_PER_KM,
                        elevM = rs.getBigDecimal("elev_m").toDouble(),
                        secs = rs.getLong("secs"),
                    )
                },
                type,
            )
        var lastMonth = -1
        return rows.mapIndexed { i, w ->
            val label =
                if (i == rows.lastIndex) {
                    "esta"
                } else {
                    val withMonth = w.date.monthValue != lastMonth
                    lastMonth = w.date.monthValue
                    if (withMonth) "${w.date.dayOfMonth} ${MES[w.date.monthValue - 1]}" else "${w.date.dayOfMonth}"
                }
            val value = if (hours) round1(w.secs / SECONDS_PER_HOUR) else w.km.roundToLong().toDouble()
            VolumeWeek(label, value, subFor(type, w))
        }
    }

    private fun subFor(
        type: String,
        w: WeekAgg,
    ): String =
        when (type) {
            "run" -> distanceSub(w, "carrera", "carreras", withElev = false)
            "hike" -> distanceSub(w, "hike", "hikes")
            GYM -> gymSub(w)
            else -> distanceSub(w, "salida", "salidas")
        }

    private fun distanceSub(
        w: WeekAgg,
        one: String,
        many: String,
        withElev: Boolean = true,
    ): String {
        if (w.n == 0) return "Sin $many esta semana"
        val base = "${count(w.n, one, many)} · ${fmt1(w.km)} km"
        return if (withElev) base + elev(w.elevM) else base
    }

    private fun gymSub(w: WeekAgg): String =
        if (w.n == 0) {
            "Sin sesiones esta semana"
        } else {
            "${count(w.n, "sesión", "sesiones")} · ${fmt1(w.secs / SECONDS_PER_HOUR)}h"
        }

    private fun ytd(type: String): YtdTotals {
        val agg =
            jdbc.queryForObject(
                """
                SELECT COALESCE(sum(distance_m), 0) AS dist, COALESCE(sum(elevation_m), 0) AS elev,
                       COALESCE(sum(moving_time_s), 0) AS secs
                FROM activities WHERE type = ? AND start_date >= date_trunc('year', now())
                """.trimIndent(),
                { rs, _ ->
                    Triple(
                        rs.getBigDecimal("dist").toDouble(),
                        rs.getBigDecimal("elev").toDouble(),
                        rs.getLong("secs"),
                    )
                },
                type,
            )!!
        val (dist, elev, secs) = agg
        val hoursOnly = type == GYM
        return YtdTotals(
            distance = if (hoursOnly) null else "${groupInt((dist / METERS_PER_KM).roundToLong())} km",
            elevation = if (hoursOnly) null else "${groupInt(elev.roundToLong())} m D+",
            time = "${groupInt((secs / SECONDS_PER_HOUR).roundToLong())} h",
        )
    }

    private fun count(
        n: Int,
        one: String,
        many: String,
    ): String = "$n ${if (n == 1) one else many}"

    private fun elev(m: Double): String = if (m > 0) " · ${groupInt(m.roundToLong())} m D+" else ""

    private fun fmt1(d: Double): String = (round1(d)).toString().replace('.', ',')

    private fun round1(d: Double): Double = (d * ONE_DECIMAL).roundToLong() / ONE_DECIMAL

    private fun groupInt(n: Long): String =
        n
            .toString()
            .reversed()
            .chunked(GROUP_SIZE)
            .joinToString(" ")
            .reversed()

    private fun isoDate(ts: java.sql.Timestamp): String = ts.toLocalDateTime().toLocalDate().toString()

    private data class WeekAgg(
        val date: LocalDate,
        val n: Int,
        val km: Double,
        val elevM: Double,
        val secs: Long,
    )

    private companion object {
        const val METERS_PER_KM = 1000.0
        const val MS_TO_KMH = 3.6
        const val SECONDS_PER_HOUR = 3600.0
        const val ONE_DECIMAL = 10.0
        const val GROUP_SIZE = 3
        const val GYM = "gym"
        val VOLUME_SPORTS = listOf("bike", "run", "hike", "gym")
    }
}
