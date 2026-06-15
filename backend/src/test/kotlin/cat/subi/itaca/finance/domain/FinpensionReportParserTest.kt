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
            Portfolio 1      28.05.2024               1'111.11      2'222.22                       12'345.67
            Total                                     1'111.11      2'222.22                       12'345.67
            """.trimIndent()

        val report = parser.parse(text)

        assertEquals(LocalDate.of(2026, 5, 31), report.date)
        assertEquals(BigDecimal("12345.67"), report.portfolioValueChf)
        assertEquals(BigDecimal("1111.11"), report.returnYearChf)
        assertEquals(BigDecimal("2222.22"), report.returnTotalChf)
    }

    @Test
    fun `parses the year-end report with a smaller balance`() {
        val text =
            """
            Performance-Report
            as at 31.12.2024
            Portfolio 1      28.05.2024   222.22   222.22   3'210.99
            Total                         222.22   222.22   3'210.99
            """.trimIndent()

        val report = parser.parse(text)

        assertEquals(LocalDate.of(2024, 12, 31), report.date)
        assertEquals(BigDecimal("3210.99"), report.portfolioValueChf)
    }
}
