package cat.subi.itaca.training.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
    fun `log_set without an active workout returns an actionable error`() {
        val result = tools.logSet("jalón", 45.0, 10, null)
        assertFalse(result.confirmed)
        assertTrue(result.error!!.contains("start_workout"))
    }
}
