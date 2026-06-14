package cat.subi.itaca.finance.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TransactionCategorizerTest {
    private val categorizer = TransactionCategorizer()

    @Test
    fun `maps the bank category, transfers and keyword fallbacks`() {
        // Tier 1: neon's category.
        assertEquals("income", categorizer.categorize("income_salary", false, "Nicoll Curtin"))
        assertEquals("restaurants", categorizer.categorize("food", false, "Glovo"))
        assertEquals("investment", categorizer.categorize("invest", false, "finpension"))
        assertEquals("fees", categorizer.categorize("finances", false, "Swisscard AECS"))
        // A savings-Space movement is a transfer regardless of the bank category.
        assertEquals("transfers", categorizer.categorize("finances", true, "saves"))
        // Tier 2: keyword rules for the uncategorized tail; otherwise "other".
        assertEquals("groceries", categorizer.categorize("uncategorized", false, "Lidl"))
        assertEquals("other", categorizer.categorize("uncategorized", false, "Oriol Subirana Perdiguer"))
    }
}
