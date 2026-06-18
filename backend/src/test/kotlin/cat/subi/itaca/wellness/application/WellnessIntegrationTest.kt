package cat.subi.itaca.wellness.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class WellnessIntegrationTest {
    @Autowired
    lateinit var wellness: WellnessService

    @Test
    fun `upsert is idempotent on the date (re-send overwrites)`() {
        val day = LocalDate.now().minusDays(1)
        wellness.upsert(WellnessCommand(day, sleepMinutes = 440, hrvAvgMs = 60, sleepScore = 80))
        wellness.upsert(WellnessCommand(day, sleepMinutes = 450, hrvAvgMs = 65))

        val rows = wellness.recent(30)
        assertEquals(1, rows.size)
        assertEquals(450, rows.single().sleepMinutes)
        assertEquals(65, rows.single().hrvAvgMs)
    }

    @Test
    fun `query computes averages over the window`() {
        val yesterday = LocalDate.now().minusDays(1)
        wellness.upsert(WellnessCommand(LocalDate.now(), sleepMinutes = 400, hrvAvgMs = 60, restingHr = 50))
        wellness.upsert(WellnessCommand(yesterday, sleepMinutes = 500, hrvAvgMs = 70, restingHr = 52))

        val s = wellness.queryWellness(7)

        assertEquals(2, s.days.size)
        assertEquals(450, s.avgSleepMinutes)
        assertEquals(65, s.avgHrvMs)
        assertEquals(51, s.avgRestingHr)
    }
}
