package cat.subi.itaca.finance.application

import cat.subi.itaca.finance.domain.FinpensionReportParser
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
            else -> ImportResult(false, account, message = "La importación de $account aún no está disponible.")
        }
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
        return ImportResult(true, account, report.date.toString(), report.portfolioValueChf.toDouble())
    }
}
