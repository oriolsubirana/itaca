package cat.subi.itaca.finance.application

import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

data class CurrencyWorth(
    val currency: String,
    val patrimonio: Double,
)

data class FinanceSummary(
    val month: String,
    val currency: String,
    val ingresos: Double,
    val gastos: Double,
    val neto: Double,
    val gastoPorCategoria: List<CategorySpend>,
    val movimientos: List<FinanceTx>,
    val patrimonio: List<CurrencyWorth>,
)

/**
 * Chat tool of the finance context. Reuses the read model so Claude can answer
 * questions about spending, income, categories and net worth.
 */
@Service
class FinanceTools(
    private val queries: FinanceQueries,
) : ChatTools {
    @Tool(
        name = "query_finance",
        description =
            "Returns the user's finances: for a given month and currency (CHF or EUR), the income, " +
                "expenses, net, spending by category and the transactions; plus net worth per currency. " +
                "Use for ANY question about spending, income, categories, account balances or net worth. " +
                "Categories are canonical codes (groceries, restaurants, transport, housing, utilities, " +
                "health, subscriptions, shopping, leisure, travel, fuel, investment, fees, cash, work, other). " +
                "Amounts are per currency and never converted between CHF and EUR.",
    )
    fun queryFinance(
        @ToolParam(description = "Month as YYYY-MM; omit for the latest month with data", required = false)
        month: String?,
        @ToolParam(description = "Currency: CHF or EUR (default CHF)", required = false)
        currency: String?,
    ): FinanceSummary {
        val overview = queries.overview()
        val resolvedMonth = month?.trim()?.takeIf { it.isNotBlank() } ?: overview.monthOrder.lastOrNull() ?: ""
        val resolvedCurrency = currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "CHF"
        val view =
            if (resolvedMonth.isBlank()) {
                MonthView(0.0, 0.0, emptyList(), emptyList())
            } else {
                queries.month(resolvedMonth, resolvedCurrency)
            }
        val patrimonio =
            overview.accounts
                .groupBy { it.currency }
                .map { (c, accts) -> CurrencyWorth(c, accts.sumOf { it.balance }) }
        return FinanceSummary(
            month = resolvedMonth,
            currency = resolvedCurrency,
            ingresos = view.ingresos,
            gastos = view.gastos,
            neto = view.ingresos + view.gastos,
            gastoPorCategoria = view.categorias,
            movimientos = view.tx.take(MOVEMENTS),
            patrimonio = patrimonio,
        )
    }

    private companion object {
        const val MOVEMENTS = 20
    }
}
