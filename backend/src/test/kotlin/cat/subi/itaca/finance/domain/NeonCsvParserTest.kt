package cat.subi.itaca.finance.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeonCsvParserTest {
    private val parser = NeonCsvParser()

    @Test
    fun `parses rows with bank categories, foreign-currency lines and space transfers`() {
        val csv =
            """
            "Date";"Amount";"Original amount";"Original currency";"Exchange rate";"Description";"Subject";"Category";"Tags";"Wise";"Spaces"
            "2026-06-05";"10075.95";"";"";"";"Nicoll Curtin Technology";"Salarzahlung";"income_salary";"";"no";"no"
            "2026-06-06";"-6000.00";"";"";"";"saves";"";"finances";"";"no";"yes"
            "2025-12-29";"-5.34";"-5.74";"EUR";"1.07491";"DOLCEMANIA EL PRAT AIR";;"uncategorized";"";"no";"no"
            """.trimIndent()

        val rows = parser.parse(csv)

        assertEquals(3, rows.size)
        assertEquals(LocalDate.of(2026, 6, 5), rows[0].date)
        assertEquals(BigDecimal("10075.95"), rows[0].amount)
        assertEquals("income_salary", rows[0].bankCategory)
        assertFalse(rows[0].transfer)
        assertTrue(rows[1].transfer, "Spaces = yes is an internal transfer")
        // A foreign-currency line still has the CHF amount and an empty unquoted Subject.
        assertEquals("DOLCEMANIA EL PRAT AIR", rows[2].description)
        assertEquals(BigDecimal("-5.34"), rows[2].amount)
        assertEquals("uncategorized", rows[2].bankCategory)
    }
}
