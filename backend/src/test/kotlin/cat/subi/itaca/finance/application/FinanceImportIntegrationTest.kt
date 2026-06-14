package cat.subi.itaca.finance.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Import side: a finpension report becomes the account's latest balance snapshot. */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class FinanceImportIntegrationTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var queries: FinanceQueries

    @Test
    fun `importing a finpension report stores the portfolio value as the latest balance`() {
        val service = FinanceImportService(jdbc, PdfTextExtractor { REPORT })
        val id = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'finpension'", Long::class.java)!!

        val result = service.import(id, ByteArray(0))

        assertTrue(result.imported)
        assertEquals("2026-05-31", result.date)
        assertEquals(44020.85, result.value)
        // Newest snapshot (31.05.2026) supersedes the seeded placeholder.
        assertEquals(
            44020.85,
            queries
                .overview()
                .accounts
                .single { it.name == "finpension" }
                .balance,
        )
    }

    @Test
    fun `importing into an account without a known format reports it is unavailable`() {
        val service = FinanceImportService(jdbc, PdfTextExtractor { REPORT })
        val sabadell = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'Sabadell'", Long::class.java)!!

        assertFalse(service.import(sabadell, ByteArray(0)).imported)
    }

    private companion object {
        val REPORT =
            """
            Performance-Report
            as at 31.05.2026
            Portfolio 1   28.05.2024   2'942.78   4'520.85   44'020.85
            Total                      2'942.78   4'520.85   44'020.85
            """.trimIndent()
    }
}
