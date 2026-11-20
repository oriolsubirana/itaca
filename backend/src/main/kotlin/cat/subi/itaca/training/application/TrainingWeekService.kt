package cat.subi.itaca.training.application

import cat.subi.itaca.shared.chat.ChatTools
import cat.subi.itaca.training.domain.Compliance
import cat.subi.itaca.training.domain.PlanWeek
import cat.subi.itaca.training.domain.SessionCheck
import cat.subi.itaca.training.domain.SessionStatus
import cat.subi.itaca.training.domain.Sport
import cat.subi.itaca.training.domain.TrainingSchedule
import cat.subi.itaca.training.domain.TriathlonPlan
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class PlannedSessionView(
    val date: String,
    /** swim | bike | run | gym | race */
    val sport: String,
    val title: String,
    val detail: String,
    val minutes: Int,
    /** easy | moderate | key | long | race */
    val intensity: String,
    val gymRoutine: String?,
    val distanceKm: Double?,
    /** done | partial | missed | pending | upcoming */
    val status: String,
    val doneMinutes: Int,
)

data class DayPlanView(
    val date: String,
    val weekday: String,
    /** rest | easy | key | long | race — the key the nutrition fueling targets use. */
    val dayLoad: String,
    val sessions: List<PlannedSessionView>,
)

data class SportAdherenceView(
    val sport: String,
    val plannedMinutes: Int,
    val doneMinutes: Int,
)

data class AdherenceView(
    val percent: Int?,
    val bySport: List<SportAdherenceView>,
)

data class TrainingWeekView(
    val monday: String,
    val sunday: String,
    val phaseKey: String,
    val phaseName: String,
    val weekInPhase: Int,
    val weeksToRace: Int,
    /** load | recovery | sharpen | taper | race */
    val kind: String,
    val focus: String,
    val totalMinutes: Int,
    val days: List<DayPlanView>,
    val adherence: AdherenceView,
)

data class TodayPlanView(
    val date: String,
    val dayLoad: String,
    val sessions: List<PlannedSessionView>,
    /** Estimated kcal of today's planned sessions not done yet (for the day's calorie target). */
    val pendingKcal: Int,
)

data class TrainingWeekResult(
    val week: TrainingWeekView? = null,
    val message: String? = null,
)

/**
 * The week-by-week plan against reality: each planned session with its status (from Strava and the
 * logged gym workouts) and the week's adherence. Also feeds the day's fueling and calorie target.
 */
@Service
class TrainingWeekService(
    private val ledger: TrainingLedger,
) : ChatTools {
    @Tool(
        name = "query_training_week",
        description =
            "Week-by-week triathlon plan (Monday-Sunday) with every planned session — sport, title, detail, " +
                "minutes, intensity, gym routine — its status against what was actually done (done / partial / " +
                "missed / pending / upcoming, from Strava and the logged gym workouts), each day's load " +
                "(rest/easy/key/long/race) and the week's adherence %. Defaults to the current week; pass any " +
                "date to see that week (past weeks to review compliance, next week to plan ahead). Use it for " +
                "'what's today's session', 'what's this week', or how training went.",
    )
    fun queryWeek(
        @ToolParam(description = "Any date of the wanted week, YYYY-MM-DD. Omit for the current week", required = false)
        date: String?,
    ): TrainingWeekResult {
        val day = date?.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
        return week(day, LocalDate.now())
            ?.let { TrainingWeekResult(week = it) }
            ?: TrainingWeekResult(
                message =
                    "No plan week for $day (the plan runs 2026-07-13 to " +
                        "${TriathlonPlan.raceDate})",
            )
    }

    fun week(
        date: LocalDate,
        today: LocalDate,
    ): TrainingWeekView? {
        val week = TrainingSchedule.weekOf(date) ?: return null
        val sunday = week.monday.plusDays(DAYS_IN_WEEK - 1L)
        val done = ledger.done(week.monday, minOf(sunday, today))
        val checks = Compliance.statuses(week.sessions, done, today)
        val adherence = Compliance.adherence(week.sessions, done, today)
        return TrainingWeekView(
            monday = week.monday.toString(),
            sunday = sunday.toString(),
            phaseKey = week.phaseKey,
            phaseName = phaseName(week),
            weekInPhase = week.weekInPhase,
            weeksToRace = week.weeksToRace,
            kind = week.kind.name.lowercase(),
            focus = week.focus,
            totalMinutes = week.totalMinutes,
            days =
                (0 until DAYS_IN_WEEK).map { offset ->
                    val day = week.monday.plusDays(offset.toLong())
                    val dayChecks = checks.filter { it.session.date == day }
                    DayPlanView(
                        date = day.toString(),
                        weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, SPANISH),
                        dayLoad = TrainingSchedule.dayLoadOf(dayChecks.map { it.session }).wire,
                        sessions = dayChecks.map(::toView),
                    )
                },
            adherence =
                AdherenceView(
                    percent = adherence.percent,
                    bySport =
                        adherence.bySport.map {
                            SportAdherenceView(it.sport.wire(), it.plannedMinutes, it.doneMinutes)
                        },
                ),
        )
    }

    fun today(today: LocalDate): TodayPlanView {
        val planned = TrainingSchedule.sessionsOn(today)
        val checks = Compliance.statuses(planned, ledger.done(today, today), today)
        val kcalPerMinute = ledger.kcalPerMinute(today)
        val pendingKcal =
            checks
                .filter { it.status != SessionStatus.DONE }
                .sumOf { check ->
                    val remaining = (check.session.minutes - check.doneMinutes).coerceAtLeast(0)
                    val sport = check.session.sport
                    remaining * (kcalPerMinute[sport] ?: FALLBACK_KCAL_PER_MIN.getValue(sport))
                }
        return TodayPlanView(
            date = today.toString(),
            dayLoad = TrainingSchedule.dayLoadOf(planned).wire,
            sessions = checks.map(::toView),
            pendingKcal = pendingKcal.roundToInt(),
        )
    }

    /** The routine of the next planned gym session from today on (this week or next), if any. */
    fun nextPlannedGymRoutine(today: LocalDate): String? {
        val doneToday = ledger.done(today, today).any { it.sport == Sport.GYM }
        return (0..DAYS_IN_WEEK)
            .asSequence()
            .map { today.plusDays(it.toLong()) }
            .filterNot { it == today && doneToday }
            .flatMap { TrainingSchedule.sessionsOn(it) }
            .firstNotNullOfOrNull { it.gymRoutine }
    }

    private fun toView(check: SessionCheck) =
        PlannedSessionView(
            date = check.session.date.toString(),
            sport = check.session.sport.wire(),
            title = check.session.title,
            detail = check.session.detail,
            minutes = check.session.minutes,
            intensity =
                check.session.intensity.name
                    .lowercase(),
            gymRoutine = check.session.gymRoutine,
            distanceKm = check.session.distanceKm,
            status = check.status.name.lowercase(),
            doneMinutes = check.doneMinutes,
        )

    private fun phaseName(week: PlanWeek): String = TriathlonPlan.phases.first { it.key == week.phaseKey }.name

    private fun Sport.wire(): String = name.lowercase()

    private companion object {
        const val DAYS_IN_WEEK = 7
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")

        // Rough kcal/min until Strava history provides real ones.
        val FALLBACK_KCAL_PER_MIN =
            mapOf(
                Sport.SWIM to 9.0,
                Sport.RUN to 12.0,
                Sport.BIKE to 10.0,
                Sport.GYM to 6.0,
                Sport.RACE to 11.0,
            )
    }
}
