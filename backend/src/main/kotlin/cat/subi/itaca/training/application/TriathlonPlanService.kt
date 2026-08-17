package cat.subi.itaca.training.application

import cat.subi.itaca.shared.chat.ChatTools
import cat.subi.itaca.training.domain.RaceTarget
import cat.subi.itaca.training.domain.TemplateDay
import cat.subi.itaca.training.domain.TriathlonPlan
import org.springframework.ai.tool.annotation.Tool
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.roundToInt

data class PhaseView(
    val key: String,
    val name: String,
    val objective: String,
    val week: Int,
    val totalWeeks: Int,
    val guidance: List<String>,
    val milestone: String,
)

data class SportProgress(
    val sessions: Int,
    val km: Double,
    val hours: Double,
    // Spanish, ready to display: swim pace /100m, run pace /km, bike km/h.
    val pace: String?,
)

data class PlanProgress(
    val windowDays: Int,
    val swim: SportProgress,
    val run: SportProgress,
    val bike: SportProgress,
    val longestRunKm: Double?,
    val longestRunPace: String?,
)

data class TriathlonPlanView(
    val raceName: String,
    val raceDate: String,
    val daysToRace: Long,
    val goal: String,
    val phase: PhaseView?,
    val nextPhaseStart: String?,
    val weeklyTemplate: List<TemplateDay>,
    val raceTargets: List<RaceTarget>,
    val principles: List<String>,
    val progress: PlanProgress,
)

/**
 * The triathlon plan as a chat tool and query service: static plan (domain) + live progress
 * computed from the imported Strava activities (swim comes in as type 'other' with a Swim sport).
 */
@Service
class TriathlonPlanService(
    private val jdbc: JdbcTemplate,
) : ChatTools {
    @Tool(
        name = "query_triathlon_plan",
        description =
            "Zurich Olympic triathlon plan (sub-2h30, June 2027): current phase with its guidance and " +
                "milestone, days to race, weekly template, race-day targets and last-4-weeks swim/run/bike " +
                "progress from Strava. Use it whenever planning or discussing endurance training.",
    )
    fun queryPlan(): TriathlonPlanView = view(LocalDate.now())

    fun view(today: LocalDate): TriathlonPlanView {
        val phase = TriathlonPlan.phaseOn(today)
        return TriathlonPlanView(
            raceName = TriathlonPlan.RACE_NAME,
            raceDate = TriathlonPlan.raceDate.toString(),
            daysToRace = TriathlonPlan.daysToRace(today),
            goal = TriathlonPlan.GOAL,
            phase =
                phase?.let {
                    PhaseView(
                        key = it.key,
                        name = it.name,
                        objective = it.objective,
                        week = it.weekOf(today),
                        totalWeeks = it.totalWeeks,
                        guidance = it.guidance,
                        milestone = it.milestone,
                    )
                },
            nextPhaseStart =
                TriathlonPlan.phases
                    .firstOrNull { it.start.isAfter(today) }
                    ?.start
                    ?.toString(),
            weeklyTemplate = TriathlonPlan.weeklyTemplate,
            raceTargets = TriathlonPlan.raceTargets,
            principles = TriathlonPlan.principles,
            progress = progress(today),
        )
    }

    private fun progress(today: LocalDate): PlanProgress {
        val since = today.minusDays(WINDOW_DAYS.toLong())
        val swim = sportTotals("type = 'other' AND lower(coalesce(sport, '')) LIKE 'swim%'", since)
        val run = sportTotals("type = 'run'", since)
        val bike = sportTotals("type = 'bike'", since)

        val longestRun =
            jdbc
                .queryForList(
                    """
                    SELECT distance_m, moving_time_s FROM activities
                    WHERE type = 'run' AND start_date >= ? AND distance_m > 0
                    ORDER BY distance_m DESC LIMIT 1
                    """.trimIndent(),
                    since,
                ).firstOrNull()

        val longestRunM = (longestRun?.get("distance_m") as? Number)?.toDouble()
        val longestRunS = (longestRun?.get("moving_time_s") as? Number)?.toInt()
        return PlanProgress(
            windowDays = WINDOW_DAYS,
            swim = swim.copy(pace = swimPace(swim)),
            run = run.copy(pace = runPace(run.km, run.hours)),
            bike = bike.copy(pace = bikeSpeed(bike)),
            longestRunKm = longestRunM?.let { it / M_PER_KM },
            longestRunPace =
                if (longestRunM != null && longestRunS != null && longestRunM > 0) {
                    runPace(longestRunM / M_PER_KM, longestRunS / S_PER_HOUR)
                } else {
                    null
                },
        )
    }

    private fun sportTotals(
        where: String,
        since: LocalDate,
    ): SportProgress {
        val row =
            jdbc.queryForMap(
                """
                SELECT count(*) AS sessions,
                       coalesce(sum(distance_m), 0) AS meters,
                       coalesce(sum(moving_time_s), 0) AS seconds
                FROM activities WHERE $where AND start_date >= ?
                """.trimIndent(),
                since,
            )
        val meters = (row["meters"] as Number).toDouble()
        val seconds = (row["seconds"] as Number).toDouble()
        return SportProgress(
            sessions = (row["sessions"] as Number).toInt(),
            km = meters / M_PER_KM,
            hours = seconds / S_PER_HOUR,
            pace = null,
        )
    }

    private fun swimPace(swim: SportProgress): String? {
        if (swim.km <= 0 || swim.hours <= 0) return null
        val secondsPer100 = (swim.hours * S_PER_HOUR) / (swim.km * M_PER_KM / SWIM_SPLIT_M)
        return "${formatMinSec(secondsPer100)} /100m"
    }

    private fun runPace(
        km: Double,
        hours: Double,
    ): String? {
        if (km <= 0 || hours <= 0) return null
        return "${formatMinSec(hours * S_PER_HOUR / km)} /km"
    }

    private fun bikeSpeed(bike: SportProgress): String? {
        if (bike.km <= 0 || bike.hours <= 0) return null
        return "${((bike.km / bike.hours) * TENTHS).roundToInt() / TENTHS} km/h"
    }

    private fun formatMinSec(totalSeconds: Double): String {
        val s = totalSeconds.roundToInt()
        return "%d:%02d".format(s / SECONDS_PER_MINUTE, s % SECONDS_PER_MINUTE)
    }

    private companion object {
        const val WINDOW_DAYS = 28
        const val M_PER_KM = 1000.0
        const val S_PER_HOUR = 3600.0
        const val SECONDS_PER_MINUTE = 60
        const val SWIM_SPLIT_M = 100.0
        const val TENTHS = 10.0
    }
}
