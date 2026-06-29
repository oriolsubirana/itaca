package cat.subi.itaca.training.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the chat tools against the real schema and seeds (last completed
 * workout: Push on 2026-06-09 -> next in rotation: Pull, Jalón at 45 kg x 12).
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class TrainingToolsIntegrationTest {
    @Autowired
    lateinit var tools: TrainingTools

    @Autowired
    lateinit var queries: TrainingQueries

    @Autowired
    lateinit var history: TrainingHistoryQueries

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `gym history exposes sessions and per-exercise progression`() {
        assertEquals(3, history.sessions().size)
        assertEquals(10, history.exercises().size)
        val jalon = history.exercises().single { it.name == "Jalón al pecho" }
        val prog = history.exerciseProgression(jalon.id)
        assertEquals("3×6-8", prog.target)
        assertEquals(45.0, prog.lastWeight)
        assertEquals(12, prog.lastReps)
        assertEquals(47.5, prog.suggestedWeight, "12 reps exceed the 8-rep target -> +2.5 kg")
    }

    @Test
    fun `query_exercise_history finds an exercise's last set regardless of routine`() {
        val militar = tools.queryExerciseHistory("press militar")
        assertTrue(militar.found, "Press Militar has a completed set in the seed (Push 2026-06-09)")
        assertEquals(14.0, militar.lastWeightKg)
        assertEquals(6, militar.lastReps)
        assertEquals(14.0, militar.suggestedWeightKg, "6 reps do not exceed the 8-rep target -> keep weight")

        val unknown = tools.queryExerciseHistory("dominadas a una mano")
        assertFalse(unknown.found)
    }

    @Test
    fun `home summary reflects the last completed session and the next rotation`() {
        val summary = queries.homeSummary()
        assertEquals("2026-06-09", summary.lastWorkoutDate)
        assertEquals("Push", summary.lastWorkoutRoutine)
        assertEquals("Pull", summary.nextRoutine)
    }

    @Test
    fun `full workout flow with rotation, progression and previous-session comparison`() {
        val started = tools.startWorkout(null)
        assertEquals("Pull", started.routineName)
        assertFalse(started.alreadyActive)
        val jalon = started.plan.first()
        assertEquals("Jalón al pecho", jalon.exerciseName)
        assertEquals(45.0, jalon.lastWeightKg)
        assertEquals(12, jalon.lastReps)
        assertEquals(47.5, jalon.suggestedWeightKg, "12 reps exceed the 8-rep target with margin -> +2.5 kg")

        val again = tools.startWorkout(null)
        assertTrue(again.alreadyActive, "starting twice must not create a second active workout")

        val logged = tools.logSet("jalón", 47.5, 8, null)
        assertTrue(logged.confirmed)
        assertEquals("Jalón al pecho", logged.exerciseName)
        assertEquals(1, logged.setNumberForExercise)
        assertEquals(47.5, logged.suggestedNextWeightKg, "8 reps meet but do not exceed the target -> keep weight")
        assertEquals(false, logged.exceededTargetWithMargin)

        val ambiguous = tools.logSet("press", 50.0, 8, null)
        assertFalse(ambiguous.confirmed)
        assertTrue((ambiguous.candidates ?: emptyList()).size >= 2, "press matches several exercises")

        val ended = tools.endWorkout("buena sesión")
        assertTrue(ended.completed)
        assertEquals("Pull", ended.routineName)
        assertEquals(1, ended.totalSets)
        val comparison = ended.comparison.single { it.exerciseName == "Jalón al pecho" }
        assertEquals(47.5, comparison.topWeightKg)
        assertEquals(45.0, comparison.previousTopWeightKg)
        assertEquals(12, comparison.previousTopReps)

        val recent = tools.queryWorkouts(10)
        assertNotNull(recent.firstOrNull { it.routineName == "Pull" && it.completed && it.sets.size == 1 })
    }

    @Test
    fun `query_activities exposes imported Strava rides and per-sport totals`() {
        jdbc.update(
            """
            INSERT INTO activities (strava_id, type, name, start_date, distance_m, moving_time_s, elevation_m, avg_hr)
            VALUES (90001, 'bike', 'Ruta', now(), 42300.0, 5880, 620.0, 142.0)
            """.trimIndent(),
        )

        val summary = tools.queryActivities("bike", 10)

        val ride = summary.recent.single()
        assertEquals("bike", ride.type)
        assertEquals(98, ride.durationMin, "5880 s / 60")
        assertNotNull(ride.distanceKm)
        assertTrue(summary.totals.any { it.sport == "bike" })
    }

    @Test
    fun `log_set without an active workout returns an actionable error`() {
        val result = tools.logSet("jalón", 45.0, 10, null)
        assertFalse(result.confirmed)
        assertTrue(result.error!!.contains("start_workout"))
    }
}
