package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class ComplianceTest {
    private val mon = LocalDate.parse("2026-10-12")
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)

    private fun planned(
        date: LocalDate,
        sport: Sport,
        minutes: Int,
    ) = PlannedSession(date, sport, "$sport", "", minutes, Intensity.EASY)

    @Test
    fun `a session is done at 75 percent of the planned time, partial below, missed when nothing`() {
        val plan = listOf(planned(mon, Sport.RUN, 40), planned(mon, Sport.SWIM, 40), planned(mon, Sport.BIKE, 60))
        val done = listOf(DoneActivity(mon, Sport.RUN, 30), DoneActivity(mon, Sport.SWIM, 10))

        val statuses = Compliance.statuses(plan, done, today = tue).map { it.status }

        assertEquals(listOf(SessionStatus.DONE, SessionStatus.PARTIAL, SessionStatus.MISSED), statuses)
    }

    @Test
    fun `today's unmatched session is pending and future ones upcoming`() {
        val plan = listOf(planned(tue, Sport.RUN, 40), planned(wed, Sport.BIKE, 60))

        val statuses = Compliance.statuses(plan, emptyList(), today = tue).map { it.status }

        assertEquals(listOf(SessionStatus.PENDING, SessionStatus.UPCOMING), statuses)
    }

    @Test
    fun `two sessions of the same sport on a day share that day's minutes in order`() {
        val plan = listOf(planned(mon, Sport.RUN, 30), planned(mon, Sport.RUN, 20))
        val done = listOf(DoneActivity(mon, Sport.RUN, 35))

        val statuses = Compliance.statuses(plan, done, today = tue).map { it.status }

        assertEquals(listOf(SessionStatus.DONE, SessionStatus.PARTIAL), statuses)
    }

    @Test
    fun `gym counts as done with any logged session that day`() {
        val plan = listOf(planned(mon, Sport.GYM, 50))
        val done = listOf(DoneActivity(mon, Sport.GYM, 0))

        assertEquals(SessionStatus.DONE, Compliance.statuses(plan, done, today = tue).single().status)
    }

    @Test
    fun `the race is done with any swim, bike or run that day`() {
        val race = LocalDate.parse("2027-06-27")
        val plan = listOf(planned(race, Sport.RACE, 150))
        val done = listOf(DoneActivity(race, Sport.SWIM, 27), DoneActivity(race, Sport.BIKE, 72))

        assertEquals(SessionStatus.DONE, Compliance.statuses(plan, done, today = race.plusDays(1)).single().status)
    }

    @Test
    fun `weekly adherence credits moved sessions per sport, capped at the plan, up to today`() {
        val plan =
            listOf(
                planned(mon, Sport.RUN, 40),
                planned(tue, Sport.BIKE, 60),
                planned(wed, Sport.RUN, 40),
            )
        // The Monday run was done on Tuesday and was longer than planned; the bike was skipped.
        val done = listOf(DoneActivity(tue, Sport.RUN, 70))

        val adherence = Compliance.adherence(plan, done, today = tue)

        val run = adherence.bySport.single { it.sport == Sport.RUN }
        assertEquals(40, run.plannedMinutes)
        assertEquals(40, run.doneMinutes)
        assertEquals(0, adherence.bySport.single { it.sport == Sport.BIKE }.doneMinutes)
        assertEquals(40, adherence.percent)
    }

    @Test
    fun `adherence with nothing planned yet is null`() {
        assertEquals(null, Compliance.adherence(emptyList(), emptyList(), today = mon).percent)
    }
}
