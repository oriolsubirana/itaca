package cat.subi.itaca.training.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan vs reality over the real schema: base week 14 (Mon 2026-10-12), viewed on Thursday.
 * Planned: Mon swim 45 + Push · Tue run 40 · Wed bike 60 · Thu swim 45 + Pull · Sat long bike · Sun long run.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class TrainingPlanTrackingIntegrationTest {
    @Autowired
    lateinit var weeks: TrainingWeekService

    @Autowired
    lateinit var load: TrainingLoadService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private val monday = LocalDate.parse("2026-10-12")
    private val thursday = monday.plusDays(3)
    private var stravaId = 1000L

    /** [kind]: "swim" (Strava type 'other' + sport Swim), "run" or "bike". */
    private fun activity(
        start: String,
        kind: String,
        minutes: Int,
        avgHr: Double? = null,
        calories: Int? = null,
    ) {
        jdbc.update(
            """
            INSERT INTO activities (strava_id, type, sport, name, start_date, distance_m, moving_time_s, avg_hr, calories)
            VALUES (?, ?, ?, 'test', ?, 1000, ?, ?, ?)
            """.trimIndent(),
            stravaId++,
            if (kind == "swim") "other" else kind,
            kind.replaceFirstChar(Char::uppercase),
            OffsetDateTime.parse(start),
            minutes * 60,
            avgHr,
            calories,
        )
    }

    private fun gymWorkout(date: LocalDate) {
        jdbc.update(
            """
            INSERT INTO workouts (date, routine_id, completed)
            VALUES (?, (SELECT id FROM routines WHERE name = 'Push'), true)
            """.trimIndent(),
            java.sql.Date.valueOf(date),
        )
    }

    private fun seedWeek() {
        activity("2026-10-12T07:30:00+02:00", "swim", 45)
        gymWorkout(monday)
        activity("2026-10-13T19:00:00+02:00", "run", 20)
    }

    @Test
    fun `each planned session gets its status from Strava and the gym log`() {
        seedWeek()

        val week = assertNotNull(weeks.week(monday, today = thursday))

        assertEquals("base", week.phaseKey)
        assertEquals(14, week.weekInPhase)
        assertEquals("load", week.kind)
        assertEquals(listOf("done", "done"), week.days[0].sessions.map { it.status })
        assertEquals(
            "partial",
            week.days[1]
                .sessions
                .single()
                .status,
        )
        assertEquals(
            "missed",
            week.days[2]
                .sessions
                .single()
                .status,
        )
        assertEquals(listOf("pending", "pending"), week.days[3].sessions.map { it.status })
        assertTrue(week.days[4].sessions.isEmpty(), "Friday is rest")
        assertEquals("rest", week.days[4].dayLoad)
        assertEquals(
            "upcoming",
            week.days[5]
                .sessions
                .single()
                .status,
        )
        assertEquals("long", week.days[5].dayLoad)
        assertEquals(
            "Push",
            week.days[0]
                .sessions
                .single { it.sport == "gym" }
                .gymRoutine,
        )
        assertEquals("lunes", week.days[0].weekday)
    }

    @Test
    fun `weekly adherence counts what was due up to today`() {
        seedWeek()

        val adherence = assertNotNull(weeks.week(monday, today = thursday)).adherence

        // Due by Thursday: swim 90 + gym 100 + run 40 + bike 60 = 290; done: 45 + 50 + 20 + 0 = 115.
        assertEquals(40, adherence.percent)
        assertEquals(20, adherence.bySport.single { it.sport == "run" }.doneMinutes)
    }

    @Test
    fun `today's view estimates the kcal still to burn from the Strava history`() {
        // Half a year of swims at 10 kcal/min sets the swim estimate; gym falls back to 6 kcal/min.
        activity("2026-09-01T07:30:00+02:00", "swim", 60, calories = 600)

        val today = weeks.today(thursday)

        assertEquals("easy", today.dayLoad)
        assertEquals(listOf("swim", "gym"), today.sessions.map { it.sport })
        assertEquals(45 * 10 + 50 * 6, today.pendingKcal)
    }

    @Test
    fun `next planned gym routine skips today's once it is logged`() {
        assertEquals("Pull", weeks.nextPlannedGymRoutine(thursday))
        jdbc.update(
            """
            INSERT INTO workouts (date, routine_id, completed)
            VALUES (?, (SELECT id FROM routines WHERE name = 'Pull'), true)
            """.trimIndent(),
            java.sql.Date.valueOf(thursday),
        )
        // Next gym after Thursday is next Monday's Push.
        assertEquals("Push", weeks.nextPlannedGymRoutine(thursday))
    }

    @Test
    fun `no plan week outside the plan`() {
        assertNull(weeks.week(LocalDate.parse("2027-07-05"), today = thursday))
        assertTrue(weeks.today(LocalDate.parse("2027-07-05")).sessions.isEmpty())
    }

    @Test
    fun `training load builds fitness and fatigue from the activities`() {
        // Three weeks of a daily 60-min run at threshold-ish heart rate, then the view.
        (0L until 21L).forEach { d ->
            val day = monday.minusDays(21 - d)
            activity("${day}T08:00:00+02:00", "run", 60, avgHr = 160.0)
        }

        val view = load.view(monday, days = 28)

        assertEquals(28, view.series.size)
        assertTrue(view.today.fatigue > view.today.fitness, "fatigue reacts faster than fitness")
        assertTrue(view.today.form < 0)
        assertEquals("overreaching", view.status)
        assertTrue(view.rampWarning, "fitness climbing fast from zero")
        assertEquals(0, view.series.first().load, "nothing before the block")
    }
}
