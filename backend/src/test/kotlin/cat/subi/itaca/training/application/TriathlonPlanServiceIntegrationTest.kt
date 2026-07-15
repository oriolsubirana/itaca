package cat.subi.itaca.training.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class TriathlonPlanServiceIntegrationTest {
    @Autowired
    lateinit var service: TriathlonPlanService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private val today = LocalDate.parse("2026-08-01")

    private data class Act(
        val stravaId: Long,
        val type: String,
        val sport: String?,
        val daysAgo: Long,
        val meters: Double,
        val seconds: Int,
    )

    private fun insertActivity(a: Act) {
        jdbc.update(
            """
            INSERT INTO activities (strava_id, type, sport, name, start_date, distance_m, moving_time_s)
            VALUES (?, ?, ?, 'test', ?, ?, ?)
            """.trimIndent(),
            a.stravaId,
            a.type,
            a.sport,
            today.minusDays(a.daysAgo).atStartOfDay(),
            a.meters,
            a.seconds,
        )
    }

    @Test
    fun `computes last-4-weeks swim, run and bike progress from activities`() {
        // Swim arrives from Strava as type 'other' with a Swim sport; 2000 m in 40 min = 2:00 /100m.
        insertActivity(Act(1, "other", "Swim", 2, 1000.0, 1200))
        insertActivity(Act(2, "other", "Swim", 5, 1000.0, 1200))
        // Runs: 5 km in 25 min + 10 km in 55 min (the long one).
        insertActivity(Act(3, "run", "Run", 3, 5000.0, 1500))
        insertActivity(Act(4, "run", "Run", 6, 10000.0, 3300))
        // Bike: 60 km in 2 h = 30 km/h. One stale ride outside the window must not count.
        insertActivity(Act(5, "bike", "Ride", 4, 60000.0, 7200))
        insertActivity(Act(6, "bike", "Ride", 40, 100000.0, 12000))
        // A gym 'other' without swim sport must not pollute the swim numbers.
        insertActivity(Act(7, "other", "WeightTraining", 1, 0.0, 3600))

        val progress = service.view(today).progress

        assertEquals(2, progress.swim.sessions)
        assertEquals(2.0, progress.swim.km)
        assertEquals("2:00 /100m", progress.swim.pace)

        assertEquals(2, progress.run.sessions)
        assertEquals(15.0, progress.run.km)
        assertEquals("5:20 /km", progress.run.pace)
        assertEquals(10.0, progress.longestRunKm)
        assertEquals("5:30 /km", progress.longestRunPace)

        assertEquals(1, progress.bike.sessions)
        assertEquals(60.0, progress.bike.km)
        assertEquals("30.0 km/h", progress.bike.pace)
    }

    @Test
    fun `resolves phase and countdown for the view date`() {
        val view = service.view(LocalDate.parse("2026-08-01"))

        val phase = assertNotNull(view.phase)
        assertEquals("base", phase.key)
        assertEquals(3, phase.week)
        assertEquals(330, view.daysToRace)
        assertEquals("2026-11-01", view.nextPhaseStart)
        assertNull(service.view(LocalDate.parse("2027-06-28")).phase, "after the race there is no phase")
    }

    @Test
    fun `empty window yields zeroed progress without paces`() {
        val progress = service.view(LocalDate.parse("2026-08-01")).progress

        assertEquals(0, progress.swim.sessions)
        assertNull(progress.swim.pace)
        assertNull(progress.longestRunKm)
    }
}
