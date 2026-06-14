package cat.subi.itaca.finance.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

/** Pure parsing of the text extracted from a finpension performance report (machine-generated, fixed layout). */
class FinpensionReportParserTest {
    private val parser = FinpensionReportParser()

    @Test
    fun `extracts the report date, portfolio value and returns from the overview`() {
        val text =
            """
            Performance-Report
            as at 31.05.2026

            Portfolio        First receipt of money   Return 2026   Return since the beginning*   Value as at 31.05.2026
            Portfolio 1      28.05.2024               2'942.78      4'520.85                       44'020.85
            Total                                     2'942.78      4'520.85                       44'020.85
            """.trimIndent()

        val report = parser.parse(text)

        assertEquals(LocalDate.of(2026, 5, 31), report.date)
        assertEquals(BigDecimal("44020.85"), report.portfolioValueChf)
        assertEquals(BigDecimal("2942.78"), report.returnYearChf)
        assertEquals(BigDecimal("4520.85"), report.returnTotalChf)
    }

    @Test
    fun `parses the year-end report with a smaller balance`() {
        val text =
            """
            Performance-Report
            as at 31.12.2024
            Portfolio 1      28.05.2024   345.15   345.15   7'145.15
            Total                         345.15   345.15   7'145.15
            """.trimIndent()

        val report = parser.parse(text)

        assertEquals(LocalDate.of(2024, 12, 31), report.date)
        assertEquals(BigDecimal("7145.15"), report.portfolioValueChf)
    }
}
