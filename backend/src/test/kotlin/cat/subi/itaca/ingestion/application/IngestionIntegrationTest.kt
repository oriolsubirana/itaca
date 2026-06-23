package cat.subi.itaca.ingestion.application

import cat.subi.itaca.TestcontainersConfiguration
import cat.subi.itaca.ingestion.BankStatementReceived
import cat.subi.itaca.ingestion.LabReportReceived
import cat.subi.itaca.ingestion.MedicalDocumentReceived
import cat.subi.itaca.ingestion.application.IngestionClassifierAi
import cat.subi.itaca.ingestion.domain.Destination
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.nio.file.Files
import kotlin.test.assertEquals

/**
 * Intake -> deterministic classification -> routing event. Asserts the routing event
 * published synchronously by [IngestionService.process] (the consuming contexts react
 * asynchronously and are covered by their own listeners).
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class, IngestionIntegrationTest.StubClassifier::class)
@RecordApplicationEvents
class IngestionIntegrationTest {
    /** Stubs the AI tier so a generic PDF routes to a clinical document (no real Anthropic call). */
    @TestConfiguration
    class StubClassifier {
        @Bean
        @Primary
        fun stubAi(): IngestionClassifierAi =
            object : IngestionClassifierAi {
                override fun classify(
                    filename: String,
                    content: ByteArray,
                ): Destination = Destination.HEALTH_DOCUMENT
            }
    }

    @Autowired
    lateinit var ingestion: IngestionService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var events: ApplicationEvents

    @Test
    fun `a CSV is registered as pending and routed to finance`() {
        val dto = ingestion.ingest("test", "neon-export.csv", "\"Date\";\"Amount\"\n".toByteArray())

        assertEquals("csv", dto.type)
        assertEquals("pending", dto.status)

        ingestion.process(dto.id)

        assertEquals(1, events.stream(BankStatementReceived::class.java).filter { it.ingestionId == dto.id }.count())
    }

    @Test
    fun `a lab-named PDF is routed to health`() {
        val dto = ingestion.ingest("test", "analitica-mayo.pdf", "%PDF-1.7 fake".toByteArray())

        assertEquals("pdf", dto.type)

        ingestion.process(dto.id)

        assertEquals(1, events.stream(LabReportReceived::class.java).filter { it.ingestionId == dto.id }.count())
    }

    @Test
    fun `a generic clinical PDF routes to a medical document via the AI tier`() {
        val dto = ingestion.ingest("test", "gastro_consultation_letter.pdf", "%PDF-1.7 fake".toByteArray())

        ingestion.process(dto.id)

        assertEquals(1, events.stream(MedicalDocumentReceived::class.java).filter { it.ingestionId == dto.id }.count())
    }

    @Test
    fun `an unrecognised file is marked as an error, not routed`() {
        val dto = ingestion.ingest("test", "notes.txt", "just some text".toByteArray())

        ingestion.process(dto.id)

        assertEquals("error", status(dto.id))
        assertEquals(0, events.stream(LabReportReceived::class.java).count())
        assertEquals(0, events.stream(BankStatementReceived::class.java).count())
    }

    @Test
    fun `retry resets an errored file back to pending`() {
        val dto = ingestion.ingest("test", "notes.txt", "just some text".toByteArray())
        ingestion.process(dto.id)
        assertEquals("error", status(dto.id))

        ingestion.retry(dto.id)

        assertEquals("pending", status(dto.id))
    }

    private fun status(id: Long): String? {
        val sql = "SELECT status FROM ingested_files WHERE id = ?"
        return jdbc.queryForObject(sql, String::class.java, id)
    }

    companion object {
        private val storageDir = Files.createTempDirectory("itaca-ingest-test")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("itaca.storage.local-dir") { storageDir.toString() }
        }
    }
}
