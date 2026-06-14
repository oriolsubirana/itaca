package cat.subi.itaca.finance.domain

import java.math.BigDecimal
import java.time.LocalDate

/** One finpension performance report: the portfolio value (CHF) at a given date plus the returns. */
data class FinpensionReport(
    val date: LocalDate,
    val portfolioValueChf: BigDecimal,
    val returnYearChf: BigDecimal,
    val returnTotalChf: BigDecimal,
)

/**
 * Parses the text of a finpension "Performance-Report". The layout is fixed and
 * machine-generated, so a deterministic parse is reliable (no LLM needed): the
 * "as at DD.MM.YYYY" date and the "Total" row's three CHF figures
 * (return this year · return since the beginning · portfolio value).
 */
class FinpensionReportParser {
    fun parse(text: String): FinpensionReport {
        val dateMatch = DATE.find(text) ?: error("No 'as at DD.MM.YYYY' date in the report")
        val (dd, mm, yyyy) = dateMatch.destructured
        val totalLine =
            text
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("Total") }
                .firstOrNull { SWISS_NUMBER.findAll(it).count() >= EXPECTED_FIGURES }
                ?: error("No 'Total' row with the CHF figures")
        val figures = SWISS_NUMBER.findAll(totalLine).map { swissToBigDecimal(it.value) }.toList()
        return FinpensionReport(
            date = LocalDate.of(yyyy.toInt(), mm.toInt(), dd.toInt()),
            returnYearChf = figures.first(),
            returnTotalChf = figures[figures.size - 2],
            portfolioValueChf = figures.last(),
        )
    }

    // Swiss number: apostrophe thousands separator, dot decimal — "44'020.85" -> 44020.85
    private fun swissToBigDecimal(s: String): BigDecimal = BigDecimal(s.replace("'", ""))

    private companion object {
        val DATE = Regex("""as at (\d{2})\.(\d{2})\.(\d{4})""")
        val SWISS_NUMBER = Regex("""\d[\d']*\.\d{2}""")
        const val EXPECTED_FIGURES = 3
    }
}
