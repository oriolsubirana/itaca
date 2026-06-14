package cat.subi.itaca.finance.application

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class FinanceAccount(
    val id: Long,
    val name: String,
    val type: String,
    val currency: String,
    val balance: Double,
    // Has a statement/report parser (others use a manual balance or are derived).
    val importable: Boolean,
)

data class FinanceOverview(
    val monthOrder: List<String>,
    val accounts: List<FinanceAccount>,
)

data class CategorySpend(
    val category: String,
    val amount: Double,
)

data class FinanceTx(
    val date: String,
    val description: String,
    val category: String,
    val amount: Double,
    val account: String,
)

data class MonthView(
    val ingresos: Double,
    val gastos: Double,
    val categorias: List<CategorySpend>,
    val tx: List<FinanceTx>,
)

/**
 * Read side of the finance context: the months that have data + accounts with
 * their latest balance, and the per-month/currency breakdown (income, expenses,
 * spending by category and the transaction list). Amounts keep their native
 * currency — CHF and EUR are never summed together.
 */
@Service
class FinanceQueries(
    private val jdbc: JdbcTemplate,
) {
    fun overview(): FinanceOverview = FinanceOverview(monthOrder(), accounts())

    // Accounts with a parser in FinanceImportService; the rest use a manual balance or are derived.
    private companion object {
        val IMPORTABLE = setOf("Neon", "finpension")
    }

    private fun monthOrder(): List<String> =
        jdbc.queryForList(
            "SELECT DISTINCT to_char(date, 'YYYY-MM') AS m FROM transactions ORDER BY m",
            String::class.java,
        )

    private fun accounts(): List<FinanceAccount> =
        jdbc.query(
            """
            SELECT a.id, a.name, a.type, a.currency,
                   COALESCE(
                       (SELECT bs.balance FROM balance_snapshots bs
                        WHERE bs.account_id = a.id ORDER BY bs.date DESC LIMIT 1),
                       -- No snapshot (e.g. the savings Space): derive the balance from its movements.
                       (SELECT sum(t.amount) FROM transactions t WHERE t.account_id = a.id),
                       0
                   ) AS balance
            FROM accounts a ORDER BY a.currency, a.name
            """.trimIndent(),
        ) { rs, _ ->
            val name = rs.getString("name")
            FinanceAccount(
                id = rs.getLong("id"),
                name = name,
                type = rs.getString("type"),
                currency = rs.getString("currency"),
                balance = rs.getBigDecimal("balance").toDouble(),
                importable = name in IMPORTABLE,
            )
        }

    fun month(
        month: String,
        currency: String,
    ): MonthView {
        val totals =
            jdbc.queryForObject(
                """
                SELECT COALESCE(sum(t.amount) FILTER (WHERE t.amount > 0), 0) AS ingresos,
                       COALESCE(sum(t.amount) FILTER (WHERE t.amount < 0), 0) AS gastos
                FROM transactions t JOIN accounts a ON a.id = t.account_id
                WHERE a.currency = ? AND to_char(t.date, 'YYYY-MM') = ?
                  AND COALESCE(t.category, '') NOT IN ('transfers', 'savings')
                """.trimIndent(),
                { rs, _ -> rs.getBigDecimal("ingresos").toDouble() to rs.getBigDecimal("gastos").toDouble() },
                currency,
                month,
            )!!
        return MonthView(totals.first, totals.second, categorias(month, currency), tx(month, currency))
    }

    private fun categorias(
        month: String,
        currency: String,
    ): List<CategorySpend> =
        jdbc.query(
            """
            SELECT COALESCE(t.category, 'other') AS cat, -sum(t.amount) AS amount
            FROM transactions t JOIN accounts a ON a.id = t.account_id
            WHERE a.currency = ? AND to_char(t.date, 'YYYY-MM') = ? AND t.amount < 0
              AND COALESCE(t.category, '') NOT IN ('transfers', 'savings')
            GROUP BY COALESCE(t.category, 'other') ORDER BY amount DESC
            """.trimIndent(),
            { rs, _ -> CategorySpend(rs.getString("cat"), rs.getBigDecimal("amount").toDouble()) },
            currency,
            month,
        )

    private fun tx(
        month: String,
        currency: String,
    ): List<FinanceTx> =
        jdbc.query(
            """
            SELECT t.date, t.description, COALESCE(t.category, 'other') AS category, t.amount, a.name AS account
            FROM transactions t JOIN accounts a ON a.id = t.account_id
            WHERE a.currency = ? AND to_char(t.date, 'YYYY-MM') = ?
              AND COALESCE(t.category, '') NOT IN ('transfers', 'savings')
            ORDER BY t.date DESC, t.id DESC
            """.trimIndent(),
            { rs, _ ->
                FinanceTx(
                    date = rs.getDate("date").toLocalDate().toString(),
                    description = rs.getString("description"),
                    category = rs.getString("category"),
                    amount = rs.getBigDecimal("amount").toDouble(),
                    account = rs.getString("account"),
                )
            },
            currency,
            month,
        )
}
