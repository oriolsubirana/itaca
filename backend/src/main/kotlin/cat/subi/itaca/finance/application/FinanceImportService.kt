package cat.subi.itaca.finance.application

import cat.subi.itaca.finance.domain.FinpensionReportParser
import cat.subi.itaca.finance.domain.NeonCsvParser
import cat.subi.itaca.finance.domain.TransactionCategorizer
import org.slf4j.LoggerFactory
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
) {
    private val log = LoggerFactory.getLogger(FinanceImportService::class.java)
    private val finpensionParser = FinpensionReportParser()
    private val neonParser = NeonCsvParser()
    private val categorizer = TransactionCategorizer()

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
            "finpension" -> importFinpension(accountId, account, bytes)
            "Neon" -> importNeon(accountId, account, bytes)
            else -> ImportResult(false, account, message = "La importación de $account aún no está disponible.")
        }
    }

    /**
     * Replaces the account's transactions in the date range the CSV covers, so re-importing
     * the same statement is idempotent. neon amounts are already in CHF.
     */
    private fun importNeon(
        accountId: Long,
        account: String,
        bytes: ByteArray,
    ): ImportResult {
        val rows = neonParser.parse(bytes.decodeToString())
        if (rows.isEmpty()) return ImportResult(false, account, message = "El CSV no contiene movimientos.")
        val from = rows.minOf { it.date }
        val to = rows.maxOf { it.date }
        jdbc.update(
            "DELETE FROM transactions WHERE account_id = ? AND date BETWEEN ? AND ?",
            accountId,
            Date.valueOf(from),
            Date.valueOf(to),
        )
        rows.forEach { r ->
            jdbc.update(
                "INSERT INTO transactions (account_id, date, amount, description, category) VALUES (?, ?, ?, ?, ?)",
                accountId,
                Date.valueOf(r.date),
                r.amount,
                r.description,
                categorizer.categorize(r.bankCategory, r.transfer, r.description),
            )
        }
        log.info("Imported {} neon transactions ({}..{})", rows.size, from, to)
        return ImportResult(true, account, "$from … $to", null, "${rows.size} movimientos importados.")
    }

    private fun importFinpension(
        accountId: Long,
        account: String,
        bytes: ByteArray,
    ): ImportResult {
        val report = finpensionParser.parse(pdf.extract(bytes))
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
}
