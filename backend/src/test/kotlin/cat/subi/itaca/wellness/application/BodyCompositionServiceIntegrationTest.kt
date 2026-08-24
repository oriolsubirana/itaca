package cat.subi.itaca.wellness.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class BodyCompositionServiceIntegrationTest {
    @Autowired
    lateinit var service: BodyCompositionService

    @Test
    fun `upserts idempotently on the date, keeping known fields when the resend omits them`() {
        val date = LocalDate.now().minusDays(1)
        service.upsert(BodyCompositionCommand(date, weightKg = 71.5, bodyFatPct = 15.2, muscleKg = 57.1))

        val updated = service.upsert(BodyCompositionCommand(date, weightKg = 71.3, bmi = 21.8))

        assertEquals(71.3, updated.weightKg, "weight always takes the fresh value")
        assertEquals(21.8, updated.bmi)
        assertEquals(15.2, updated.bodyFatPct, "omitted fields keep their stored value")
        assertEquals(1, service.recent(7).size, "same date must not create a second row")
    }

    @Test
    fun `summarizes the window newest first with the weight change`() {
        service.upsert(BodyCompositionCommand(LocalDate.now().minusDays(20), weightKg = 72.4))
        service.upsert(BodyCompositionCommand(LocalDate.now().minusDays(2), weightKg = 71.6))

        val summary = service.queryBodyComposition(30)

        assertEquals(2, summary.measurements.size)
        assertEquals(71.6, assertNotNull(summary.latest).weightKg)
        assertEquals(-0.8, summary.weightChangeKg)
    }

    @Test
    fun `a single measurement has no trend`() {
        service.upsert(BodyCompositionCommand(LocalDate.now(), weightKg = 71.0))

        assertNull(service.queryBodyComposition(30).weightChangeKg)
    }
}
