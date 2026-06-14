package cat.subi.itaca.finance.domain

import java.math.BigDecimal
import java.time.LocalDate

/** One row of a neon account-statement CSV (amount already in CHF). */
data class NeonRow(
    val date: LocalDate,
    val amount: BigDecimal,
    val description: String,
    val subject: String,
    val bankCategory: String,
    // Spaces = "yes" -> internal transfer to/from a savings Space, not real spending.
    val transfer: Boolean,
)

/**
 * Parses a neon account-statement CSV: semicolon-separated, quoted fields,
 * ISO dates, signed CHF amounts and neon's own category column. neon does not
 * embed separators inside fields, so a plain split is safe.
 */
class NeonCsvParser {
    fun parse(csv: String): List<NeonRow> =
        csv
            .lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val cells = line.split(";").map { it.trim().removeSurrounding("\"") }
                NeonRow(
                    date = LocalDate.parse(cells[COL_DATE]),
                    amount = BigDecimal(cells[COL_AMOUNT]),
                    description = cells[COL_DESCRIPTION],
                    subject = cells.getOrElse(COL_SUBJECT) { "" },
                    bankCategory = cells.getOrElse(COL_CATEGORY) { "" },
                    transfer = cells.getOrElse(COL_SPACES) { "no" }.equals("yes", ignoreCase = true),
                )
            }.toList()

    private companion object {
        const val COL_DATE = 0
        const val COL_AMOUNT = 1
        const val COL_DESCRIPTION = 5
        const val COL_SUBJECT = 6
        const val COL_CATEGORY = 7
        const val COL_SPACES = 10
    }
}
