package cat.subi.itaca.finance.application

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Read side of the finance dashboard over the seeded sample data. */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class FinanceQueriesIntegrationTest {
    @Autowired
    lateinit var queries: FinanceQueries

    @Test
    fun `overview lists the months with data and accounts with their latest balance`() {
        val overview = queries.overview()
        assertEquals(listOf("2026-04", "2026-05", "2026-06"), overview.monthOrder)
        assertEquals(5, overview.accounts.size)
        val neon = overview.accounts.single { it.name == "Neon" }
        assertEquals("CHF", neon.currency)
        assertEquals(3240.50, neon.balance)
    }

    @Test
    fun `month breaks down income, expenses and categories within one currency`() {
        val june = queries.month("2026-06", "CHF")
        assertEquals(5400.0, june.ingresos)
        assertTrue(june.gastos < 0)
        // Rent (1450) is the biggest expense category.
        assertEquals("housing", june.categorias.first().category)
        // Currency isolation: a CHF month never pulls in EUR accounts.
        assertTrue(june.tx.isNotEmpty())
        assertTrue(june.tx.all { it.account in setOf("Neon", "Revolut", "finpension") })
    }
}
