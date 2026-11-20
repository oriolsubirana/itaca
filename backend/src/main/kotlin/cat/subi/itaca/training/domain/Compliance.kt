package cat.subi.itaca.training.domain

import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/** What was actually trained (Strava activity or logged gym workout), reduced to sport + time. */
data class DoneActivity(
    val date: LocalDate,
    val sport: Sport,
    val minutes: Int,
)

enum class SessionStatus { DONE, PARTIAL, MISSED, PENDING, UPCOMING }

data class SessionCheck(
    val session: PlannedSession,
    val status: SessionStatus,
    val doneMinutes: Int,
)

data class SportAdherence(
    val sport: Sport,
    val plannedMinutes: Int,
    val doneMinutes: Int,
)

data class Adherence(
    val bySport: List<SportAdherence>,
    /** Done vs planned minutes up to today, 0-100; null while nothing was planned yet. */
    val percent: Int?,
)

/**
 * Plan vs reality. Per session: same-day, same-sport minutes (≥75 % of the plan = done). Per week:
 * minutes per sport capped at the plan, so a session moved to another day of the week still counts.
 */
object Compliance {
    private const val DONE_RATIO = 0.75
    private const val PERCENT = 100.0
    private val RACE_SPORTS = setOf(Sport.SWIM, Sport.BIKE, Sport.RUN)

    fun statuses(
        planned: List<PlannedSession>,
        done: List<DoneActivity>,
        today: LocalDate,
    ): List<SessionCheck> {
        // Minutes still unassigned per (date, sport), consumed in plan order.
        val pool =
            done
                .groupBy { it.date to it.sport }
                .mapValues { (_, acts) -> acts.sumOf { it.minutes } }
                .toMutableMap()
        val gymDays = done.filter { it.sport == Sport.GYM }.map { it.date }.toSet()
        return planned.map { session ->
            val doneMinutes =
                when (session.sport) {
                    Sport.GYM -> if (session.date in gymDays) session.minutes else 0
                    // Racing at all is the race done, whatever the clock said.
                    Sport.RACE -> if (RACE_SPORTS.any { (pool[session.date to it] ?: 0) > 0 }) session.minutes else 0
                    else -> take(pool, session.date to session.sport, session.minutes)
                }
            SessionCheck(session, statusOf(session, doneMinutes, today), doneMinutes)
        }
    }

    fun adherence(
        planned: List<PlannedSession>,
        done: List<DoneActivity>,
        today: LocalDate,
    ): Adherence {
        val due = planned.filter { !it.date.isAfter(today) && it.sport != Sport.RACE }
        val gymSessionsDone =
            done
                .filter { it.sport == Sport.GYM }
                .map { it.date }
                .distinct()
                .size
        val bySport =
            due
                .groupBy { it.sport }
                .map { (sport, sessions) ->
                    val plannedMinutes = sessions.sumOf { it.minutes }
                    val doneMinutes =
                        if (sport == Sport.GYM) {
                            // Gym has no reliable duration: credit whole sessions.
                            sessions.take(gymSessionsDone).sumOf { it.minutes }
                        } else {
                            done.filter { it.sport == sport && !it.date.isAfter(today) }.sumOf { it.minutes }
                        }
                    SportAdherence(sport, plannedMinutes, min(doneMinutes, plannedMinutes))
                }
        val planned = bySport.sumOf { it.plannedMinutes }
        val percent = if (planned == 0) null else (bySport.sumOf { it.doneMinutes } * PERCENT / planned).roundToInt()
        return Adherence(bySport, percent)
    }

    private fun take(
        pool: MutableMap<Pair<LocalDate, Sport>, Int>,
        key: Pair<LocalDate, Sport>,
        wanted: Int,
    ): Int {
        val available = pool[key] ?: 0
        val taken = min(available, wanted)
        pool[key] = available - taken
        return taken
    }

    private fun statusOf(
        session: PlannedSession,
        doneMinutes: Int,
        today: LocalDate,
    ): SessionStatus =
        when {
            doneMinutes >= session.minutes * DONE_RATIO -> SessionStatus.DONE
            doneMinutes > 0 -> SessionStatus.PARTIAL
            session.date.isAfter(today) -> SessionStatus.UPCOMING
            session.date == today -> SessionStatus.PENDING
            else -> SessionStatus.MISSED
        }
}
