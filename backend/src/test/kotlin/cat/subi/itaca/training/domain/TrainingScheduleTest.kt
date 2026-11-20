package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingScheduleTest {
    private fun week(date: String) = assertNotNull(TrainingSchedule.weekOf(LocalDate.parse(date)))

    @Test
    fun `weeks run Monday to Sunday and cover the whole runway to race day`() {
        var day = LocalDate.parse("2026-07-13")
        while (!day.isAfter(TriathlonPlan.raceDate)) {
            val w = assertNotNull(TrainingSchedule.weekOf(day), "no week for $day")
            assertEquals(DayOfWeek.MONDAY, w.monday.dayOfWeek)
            assertTrue(day >= w.monday && day <= w.monday.plusDays(6))
            day = day.plusDays(1)
        }
    }

    @Test
    fun `no schedule before the plan starts or after the race`() {
        assertNull(TrainingSchedule.weekOf(LocalDate.parse("2026-07-12")))
        assertNull(TrainingSchedule.weekOf(LocalDate.parse("2027-06-28")))
    }

    @Test
    fun `every planned session falls inside its own week`() {
        TrainingSchedule.allWeeks().forEach { w ->
            w.sessions.forEach { s ->
                assertTrue(s.date >= w.monday && s.date <= w.monday.plusDays(6), "${s.title} on ${s.date}")
                assertTrue(s.minutes > 0, "${s.title} without duration")
            }
        }
    }

    @Test
    fun `the week's phase is the one containing its Monday`() {
        assertEquals("base", week("2026-10-26").phaseKey)
        assertEquals(16, week("2026-10-26").weekInPhase)
        assertEquals("winter", week("2026-11-02").phaseKey)
        assertEquals(1, week("2026-11-02").weekInPhase)
        assertEquals("specific", week("2027-03-01").phaseKey)
    }

    @Test
    fun `mesocycles are three load weeks and one recovery week`() {
        val base = TrainingSchedule.allWeeks().filter { it.phaseKey == "base" }
        assertEquals(
            listOf(4, 8, 12, 16),
            base.filter { it.kind == WeekKind.RECOVERY }.map { it.weekInPhase },
        )
    }

    @Test
    fun `recovery weeks carry clearly less volume than the load week before`() {
        val weeks = TrainingSchedule.allWeeks()
        weeks.zipWithNext().filter { (_, b) -> b.kind == WeekKind.RECOVERY }.forEach { (load, recovery) ->
            assertTrue(
                recovery.totalMinutes <= load.totalMinutes * 0.75,
                "recovery ${recovery.monday} (${recovery.totalMinutes}) vs load (${load.totalMinutes})",
            )
            assertTrue(recovery.sessions.none { it.intensity == Intensity.KEY }, "no quality in ${recovery.monday}")
        }
    }

    @Test
    fun `the last four weeks sharpen, taper and race`() {
        assertEquals(WeekKind.SHARPEN, week("2027-05-31").kind)
        assertEquals(WeekKind.SHARPEN, week("2027-06-07").kind)
        assertEquals(WeekKind.TAPER, week("2027-06-14").kind)
        assertEquals(WeekKind.RACE, week("2027-06-21").kind)
        assertEquals(0, week("2027-06-21").weeksToRace)
    }

    @Test
    fun `the taper drops volume at least forty percent from the sharpening block`() {
        val sharpen = week("2027-05-31").totalMinutes
        assertTrue(week("2027-06-14").totalMinutes <= sharpen * 0.6)
        val raceWeekTraining = week("2027-06-21").sessions.filter { it.sport != Sport.RACE }.sumOf { it.minutes }
        assertTrue(raceWeekTraining < week("2027-06-14").totalMinutes)
    }

    @Test
    fun `race day is the race and nothing else`() {
        val sessions = TrainingSchedule.sessionsOn(TriathlonPlan.raceDate)
        assertEquals(listOf(Sport.RACE), sessions.map { it.sport })
        assertEquals(DayLoad.RACE, TrainingSchedule.dayLoadOn(TriathlonPlan.raceDate))
    }

    @Test
    fun `a full race simulation two weeks out`() {
        val sim = TrainingSchedule.sessionsOn(LocalDate.parse("2027-06-12"))
        assertEquals(listOf(Sport.SWIM, Sport.BIKE, Sport.RUN), sim.map { it.sport })
    }

    @Test
    fun `the long run builds toward 10 km in base and never exceeds 15 km`() {
        val longRuns =
            TrainingSchedule
                .allWeeks()
                .flatMap { it.sessions }
                .filter { it.sport == Sport.RUN && it.distanceKm != null }
        assertEquals(5.0, longRuns.first().distanceKm)
        assertEquals(10.0, TrainingSchedule.sessionsOn(LocalDate.parse("2026-10-25")).single().distanceKm)
        assertTrue(longRuns.all { it.distanceKm!! <= 15.0 })
    }

    @Test
    fun `run volume never jumps more than ten percent between load weeks`() {
        val loads = TrainingSchedule.allWeeks().filter { it.kind == WeekKind.LOAD && it.phaseKey == "base" }
        loads.zipWithNext().forEach { (a, b) ->
            val ka = a.sessions.mapNotNull { it.distanceKm }.sum()
            val kb = b.sessions.mapNotNull { it.distanceKm }.sum()
            assertTrue(kb <= ka * 1.1 + 0.5, "long run ${a.monday}: $ka -> ${b.monday}: $kb")
        }
    }

    @Test
    fun `gym follows the phase - two a week in base and winter, one in specific, none in the last two weeks`() {
        fun gymCount(date: String) = week(date).sessions.count { it.sport == Sport.GYM }
        assertEquals(2, gymCount("2026-09-07"))
        assertEquals(2, gymCount("2026-11-09"))
        assertEquals(1, gymCount("2027-03-08"))
        assertEquals(1, gymCount("2027-05-31"))
        assertEquals(0, gymCount("2027-06-14"))
        assertEquals(0, gymCount("2027-06-21"))
    }

    @Test
    fun `gym sessions always name a routine and keep the 3x6-8 working sets`() {
        val gym = TrainingSchedule.allWeeks().flatMap { it.sessions }.filter { it.sport == Sport.GYM }
        assertTrue(gym.isNotEmpty())
        gym.forEach {
            assertTrue(it.gymRoutine in setOf("Push", "Pull", "Leg"), "routine ${it.gymRoutine}")
            assertTrue("3×6-8" in it.detail, it.detail)
        }
        // Base keeps the Push/Pull template; winter rotates through legs too.
        val baseRoutines = gym.filter { it.date < LocalDate.parse("2026-11-02") }.map { it.gymRoutine }.toSet()
        assertEquals(setOf("Push", "Pull"), baseRoutines)
        assertTrue(gym.any { it.gymRoutine == "Leg" && it.date.monthValue in listOf(11, 12, 1, 2) })
    }

    @Test
    fun `day load reflects the heaviest session of the day`() {
        assertEquals(DayLoad.REST, TrainingSchedule.dayLoadOn(LocalDate.parse("2026-10-16")))
        assertEquals(DayLoad.LONG, TrainingSchedule.dayLoadOn(LocalDate.parse("2026-10-17")))
        assertEquals(DayLoad.EASY, TrainingSchedule.dayLoadOn(LocalDate.parse("2026-10-14")))
        assertEquals(DayLoad.KEY, TrainingSchedule.dayLoadOn(LocalDate.parse("2026-11-10")))
        assertEquals(DayLoad.REST, TrainingSchedule.dayLoadOn(LocalDate.parse("2027-07-01")))
    }
}
