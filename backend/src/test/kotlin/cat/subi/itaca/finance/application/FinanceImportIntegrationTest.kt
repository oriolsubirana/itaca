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

    @Autowired
    lateinit var accountService: FinanceAccountService

    @Test
    fun `importing a finpension report stores the portfolio value as the latest balance`() {
        val service = FinanceImportService(jdbc, PdfTextExtractor { REPORT })
        val id = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'finpension'", Long::class.java)!!

        val result = service.import(id, "%PDF fake".toByteArray())

        assertTrue(result.imported)
        assertEquals("2026-05-31", result.date)
        assertEquals(12345.67, result.value)
        // Newest snapshot (31.05.2026) supersedes the seeded placeholder.
        assertEquals(
            12345.67,
            queries
                .overview()
                .accounts
                .single { it.name == "finpension" }
                .balance,
        )
    }

    @Test
    fun `importing a neon csv inserts categorized transactions and keeps transfers out of the view`() {
        val service = FinanceImportService(jdbc, PdfTextExtractor { "" })
        val neon = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'Neon'", Long::class.java)!!
        val csv =
            """
            "Date";"Amount";"Original amount";"Original currency";"Exchange rate";"Description";"Subject";"Category";"Tags";"Wise";"Spaces"
            "2026-07-05";"10000.00";"";"";"";"Nicoll Curtin";"Salarzahlung";"income_salary";"";"no";"no"
            "2026-07-06";"-6000.00";"";"";"";"saves";"";"finances";"";"no";"yes"
            "2026-07-10";"-200.00";"";"";"";"Coop";"";"household";"";"no";"no"
            """.trimIndent()

        assertTrue(service.import(neon, csv.toByteArray()).imported)

        val view = queries.month("2026-07", "CHF")
        assertEquals(10000.0, view.ingresos)
        assertEquals(-200.0, view.gastos, "the -6000 savings move is excluded from spending")
        assertTrue(view.categorias.any { it.category == "groceries" })
        assertTrue(view.tx.none { it.category == "savings" })
        // The savings move is kept as the user's money in the Neon Saves account.
        assertEquals(
            6000.0,
            queries
                .overview()
                .accounts
                .single { it.name == "Neon Saves" }
                .balance,
        )
    }

    @Test
    fun `setting a balance anchors the account's displayed balance`() {
        val neon = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'Neon'", Long::class.java)!!

        accountService.setBalance(neon, java.math.BigDecimal("4021.30"))

        assertEquals(
            4021.30,
            queries
                .overview()
                .accounts
                .single { it.name == "Neon" }
                .balance,
        )
    }

    @Test
    fun `setting a category recategorizes the transaction`() {
        val neon = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'Neon'", Long::class.java)!!
        jdbc.update(
            "INSERT INTO transactions (account_id, date, amount, description, category) VALUES (?, ?::date, ?, ?, ?)",
            neon,
            "2026-09-01",
            java.math.BigDecimal("-50.00"),
            "Sitio raro",
            "other",
        )
        val txId =
            jdbc.queryForObject("SELECT id FROM transactions WHERE description = 'Sitio raro'", Long::class.java)!!

        accountService.setCategory(txId, "restaurants")

        assertEquals(
            "restaurants",
            queries
                .month("2026-09", "CHF")
                .tx
                .single { it.id == txId }
                .category,
        )
    }

    @Test
    fun `the AI tier recategorizes the other tail`() {
        val neon = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'Neon'", Long::class.java)!!
        jdbc.update(
            "INSERT INTO transactions (account_id, date, amount, description, category) VALUES (?, ?::date, ?, ?, ?)",
            neon,
            "2026-10-01",
            java.math.BigDecimal("-20.00"),
            "Glovo",
            "other",
        )
        val service = FinanceAiCategorizationService(jdbc) { d -> d.associateWith { "restaurants" } }

        assertEquals(1, service.categorizeOther())
        assertEquals(
            "restaurants",
            jdbc.queryForObject("SELECT category FROM transactions WHERE description = 'Glovo'", String::class.java),
        )
    }

    @Test
    fun `monthly income counts checking accounts only, not investment inflows`() {
        val finpension = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'finpension'", Long::class.java)!!
        // A positive movement in an investment account is internal wealth, not monthly income.
        jdbc.update(
            "INSERT INTO transactions (account_id, date, amount, description, category) VALUES (?, ?, ?, ?, ?)",
            finpension,
            java.sql.Date.valueOf(java.time.LocalDate.of(2026, 8, 1)),
            java.math.BigDecimal("999.00"),
            "aportación",
            "income",
        )

        assertEquals(0.0, queries.month("2026-08", "CHF").ingresos)
    }

    @Test
    fun `a file that does not match the account format is rejected, not parsed`() {
        val service = FinanceImportService(jdbc, PdfTextExtractor { REPORT })
        val finpension = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'finpension'", Long::class.java)!!
        val neon = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'Neon'", Long::class.java)!!

        // A CSV uploaded to finpension (expects a PDF) is rejected before extraction.
        assertFalse(service.import(finpension, "Date;Amount\n2026-01-01;1.0".toByteArray()).imported)
        // A PDF uploaded to Neon (expects a CSV) is rejected too.
        assertFalse(service.import(neon, "%PDF-1.7 ...".toByteArray()).imported)
    }

    @Test
    fun `importing into an account without a known format reports it is unavailable`() {
        val service = FinanceImportService(jdbc, PdfTextExtractor { REPORT })
        val myInvestor = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'MyInvestor'", Long::class.java)!!

        assertFalse(service.import(myInvestor, ByteArray(0)).imported)
    }

    private companion object {
        val REPORT =
            """
            Performance-Report
            as at 31.05.2026
            Portfolio 1   28.05.2024   1'111.11   2'222.22   12'345.67
            Total                      1'111.11   2'222.22   12'345.67
            """.trimIndent()
    }
}
