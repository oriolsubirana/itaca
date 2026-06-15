package cat.subi.itaca.finance.application

import cat.subi.itaca.finance.domain.FinpensionReportParser
import cat.subi.itaca.finance.domain.NeonCsvParser
import cat.subi.itaca.finance.domain.TransactionCategorizer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date

data class ImportResult(
    val imported: Boolean,
    val account: String,
    val date: String? = null,
    val value: Double? = null,
    val message: String? = null,
)

/**
 * Imports a statement/report into the finance context. finpension ships a fixed
 * PDF performance report, so its portfolio value is parsed deterministically and
 * stored as a balance snapshot. Bank CSV imports are added per format.
 */
@Service
class FinanceImportService(
    private val jdbc: JdbcTemplate,
    private val pdf: PdfTextExtractor,
    // Counterparty names (self, partner, joint money) whose movements are transfers, not spending.
    @Value("\${itaca.finance.transfer-names:}") transferNames: String = "",
) {
    private val log = LoggerFactory.getLogger(FinanceImportService::class.java)
    private val finpensionParser = FinpensionReportParser()
    private val neonParser = NeonCsvParser()
    private val categorizer =
        TransactionCategorizer(transferNames.split(",").map { it.trim() }.filter { it.isNotEmpty() })

    @Transactional
    fun import(
        accountId: Long,
        bytes: ByteArray,
    ): ImportResult {
        val account =
            jdbc
                .query("SELECT name FROM accounts WHERE id = ?", { rs, _ -> rs.getString("name") }, accountId)
                .firstOrNull() ?: return ImportResult(false, "?", message = "Cuenta no encontrada.")
        return when (account) {
            FinanceAccounts.FINPENSION -> importFinpension(accountId, account, bytes)
            FinanceAccounts.NEON -> importNeon(accountId, account, bytes)
            else -> ImportResult(false, account, message = "La importación de $account aún no está disponible.")
        }
    }

    /**
     * Replaces the account's transactions in the date range the CSV covers, so re-importing
     * the same statement is idempotent. neon amounts are already in CHF. Movements to/from the
     * "saves" Space are the user's own savings, so they are mirrored into the Neon Saves account
     * (money into the Space is a positive balance there) instead of counted as spending.
     */
    private fun importNeon(
        accountId: Long,
        account: String,
        bytes: ByteArray,
    ): ImportResult {
        if (isPdf(bytes)) {
            return ImportResult(false, account, message = "Sube el CSV de Neon (este fichero parece un PDF).")
        }
        val rows =
            runCatching { neonParser.parse(bytes.decodeToString()) }
                .getOrElse {
                    return ImportResult(false, account, message = "No pude leer el CSV. ¿Es el extracto de Neon?")
                }
        if (rows.isEmpty()) return ImportResult(false, account, message = "El CSV no contiene movimientos.")
        val savesId =
            jdbc.queryForObject(
                "SELECT id FROM accounts WHERE name = ?",
                Long::class.java,
                FinanceAccounts.NEON_SAVES,
            )!!
        val from = rows.minOf { it.date }
        val to = rows.maxOf { it.date }
        jdbc.update(
            "DELETE FROM transactions WHERE account_id IN (?, ?) AND date BETWEEN ? AND ?",
            accountId,
            savesId,
            Date.valueOf(from),
            Date.valueOf(to),
        )
        // Saves-Space movements are mirrored into Neon Saves (their own money); the rest are spending.
        val inserts =
            rows.map { r ->
                if (r.transfer) {
                    arrayOf<Any>(savesId, Date.valueOf(r.date), r.amount.negate(), r.description, "savings")
                } else {
                    val category = categorizer.categorize(r.bankCategory, false, r.description)
                    arrayOf<Any>(accountId, Date.valueOf(r.date), r.amount, r.description, category)
                }
            }
        jdbc.batchUpdate(
            "INSERT INTO transactions (account_id, date, amount, description, category) VALUES (?, ?, ?, ?, ?)",
            inserts,
        )
        val savings = rows.count { it.transfer }
        val spending = rows.size - savings
        log.info("Imported {} neon transactions + {} savings moves ({}..{})", spending, savings, from, to)
        return ImportResult(true, account, "$from … $to", null, "$spending movimientos · $savings al ahorro.")
    }

    private fun importFinpension(
        accountId: Long,
        account: String,
        bytes: ByteArray,
    ): ImportResult {
        if (!isPdf(bytes)) {
            return ImportResult(false, account, message = "Sube el PDF del performance report de finpension.")
        }
        val report =
            runCatching { finpensionParser.parse(pdf.extract(bytes)) }
                .getOrElse {
                    return ImportResult(false, account, message = "No pude leer el PDF. ¿Es el report de finpension?")
                }
        jdbc.update(
            """
            INSERT INTO balance_snapshots (account_id, date, balance) VALUES (?, ?, ?)
            ON CONFLICT (account_id, date) DO UPDATE SET balance = EXCLUDED.balance
            """.trimIndent(),
            accountId,
            Date.valueOf(report.date),
            report.portfolioValueChf,
        )
        log.info("Imported finpension report as at {} -> {} CHF", report.date, report.portfolioValueChf)
        return ImportResult(
            imported = true,
            account = account,
            date = report.date.toString(),
            value = report.portfolioValueChf.toDouble(),
            message = "Saldo ${report.portfolioValueChf} CHF al ${report.date}.",
        )
    }

    private fun isPdf(bytes: ByteArray): Boolean =
        bytes.size >= PDF_MAGIC.size && bytes.copyOfRange(0, PDF_MAGIC.size).contentEquals(PDF_MAGIC)

    private companion object {
        val PDF_MAGIC = "%PDF".toByteArray()
    }
}
