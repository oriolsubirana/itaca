package cat.subi.itaca.finance.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Read side of the finance dashboard over fixtures it inserts itself (no demo seed). */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
@Transactional
class FinanceQueriesIntegrationTest {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var queries: FinanceQueries

    @Autowired
    lateinit var tools: FinanceTools

    @Test
    fun `query_finance returns the month summary and net worth per currency`() {
        seedJune()
        val summary = tools.queryFinance("2026-06", "CHF")
        assertEquals(5400.0, summary.ingresos)
        assertEquals(5400.0 + summary.gastos, summary.neto)
        assertTrue(summary.gastoPorCategoria.any { it.category == "housing" })
        assertTrue(summary.patrimonio.any { it.currency == "CHF" })
    }

    @Test
    fun `overview lists the months with data and all the accounts`() {
        seedJune()
        val overview = queries.overview()
        assertEquals(listOf("2026-06"), overview.monthOrder)
        // Neon, Revolut, MyInvestor, Sabadell (031) + finpension, Neon Saves (032).
        assertEquals(6, overview.accounts.size)
    }

    @Test
    fun `month breaks down income, expenses and categories within one currency`() {
        seedJune()
        val june = queries.month("2026-06", "CHF")
        assertEquals(5400.0, june.ingresos)
        assertTrue(june.gastos < 0)
        assertEquals("housing", june.categorias.first().category)
        assertTrue(june.tx.all { it.account == "Neon" })
    }

    private fun seedJune() {
        val neon = jdbc.queryForObject("SELECT id FROM accounts WHERE name = 'Neon'", Long::class.java)!!
        insert(neon, "2026-06-25", BigDecimal("5400.00"), "Sueldo", "income")
        insert(neon, "2026-06-01", BigDecimal("-1450.00"), "Alquiler", "housing")
        insert(neon, "2026-06-03", BigDecimal("-90.00"), "Coop", "groceries")
    }

    private fun insert(
        accountId: Long,
        date: String,
        amount: BigDecimal,
        description: String,
        category: String,
    ) {
        jdbc.update(
            "INSERT INTO transactions (account_id, date, amount, description, category) VALUES (?, ?::date, ?, ?, ?)",
            accountId,
            date,
            amount,
            description,
            category,
        )
    }
}
