package cat.subi.itaca.finance.adapter.event

import cat.subi.itaca.finance.application.FinanceAccounts
import cat.subi.itaca.finance.application.FinanceImportService
import cat.subi.itaca.ingestion.BankStatementReceived
import cat.subi.itaca.ingestion.IngestionFailed
import cat.subi.itaca.ingestion.IngestionSucceeded
import cat.subi.itaca.shared.storage.DocumentStorage
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Finance's reaction to an ingested bank statement: a PDF is a finpension report, a CSV
 * is a Neon statement. Resolves the target account and runs the existing importer, then
 * reports the outcome back to ingestion.
 */
@Component
class BankStatementIngestionListener(
    private val financeImport: FinanceImportService,
    private val storage: DocumentStorage,
    private val jdbc: JdbcTemplate,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(BankStatementIngestionListener::class.java)

    // Any downstream failure must surface in the inbox as an IngestionFailed, hence the broad catch.
    @Suppress("TooGenericExceptionCaught")
    @ApplicationModuleListener
    fun on(event: BankStatementReceived) {
        try {
            val content = storage.load(event.storagePath)
            val accountName = if (isPdf(content)) FinanceAccounts.FINPENSION else FinanceAccounts.NEON
            val accountId =
                jdbc
                    .query("SELECT id FROM accounts WHERE name = ?", { rs, _ -> rs.getLong("id") }, accountName)
                    .firstOrNull()
            if (accountId == null) {
                events.publishEvent(IngestionFailed(event.ingestionId, "No encuentro la cuenta $accountName."))
                return
            }
            val result = financeImport.import(accountId, content)
            if (result.imported) {
                events.publishEvent(IngestionSucceeded(event.ingestionId, result.message ?: "Movimientos importados."))
            } else {
                events.publishEvent(IngestionFailed(event.ingestionId, result.message ?: "No se pudo importar."))
            }
        } catch (e: RuntimeException) {
            log.warn("Bank statement ingestion failed for {}", event.ingestionId, e)
            events.publishEvent(IngestionFailed(event.ingestionId, e.message ?: "No pude procesar el extracto."))
        }
    }

    private fun isPdf(bytes: ByteArray): Boolean =
        bytes.size >= PDF_MAGIC.size && bytes.copyOfRange(0, PDF_MAGIC.size).contentEquals(PDF_MAGIC)

    private companion object {
        val PDF_MAGIC = "%PDF".toByteArray()
    }
}
