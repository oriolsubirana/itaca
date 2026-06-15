package cat.subi.itaca.finance.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Lets the user anchor an account's current balance. CSV statements carry no
 * balance, so this writes today's balance snapshot (the dashboard shows the
 * latest snapshot, falling back to derived movements only when none exists).
 */
@Service
class FinanceAccountService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun setBalance(
        accountId: Long,
        balance: BigDecimal,
    ) {
        jdbc.update(
            """
            INSERT INTO balance_snapshots (account_id, date, balance) VALUES (?, CURRENT_DATE, ?)
            ON CONFLICT (account_id, date) DO UPDATE SET balance = EXCLUDED.balance
            """.trimIndent(),
            accountId,
            balance,
        )
    }

    /** Recategorize a single transaction (correct a wrong/auto category). */
    @Transactional
    fun setCategory(
        transactionId: Long,
        category: String,
    ) {
        jdbc.update("UPDATE transactions SET category = ? WHERE id = ?", category, transactionId)
    }
}
