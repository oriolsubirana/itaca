package cat.subi.itaca.finance.domain

import java.math.BigDecimal

enum class Currency { CHF, EUR }

/**
 * Monetary amount with an explicit currency. Operations across different
 * currencies require prior conversion (never implicit).
 */
data class Money(val amount: BigDecimal, val currency: Currency) {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add amounts in different currencies: $currency + ${other.currency}"
        }
        return Money(amount + other.amount, currency)
    }

    companion object {
        fun chf(amount: String): Money = Money(BigDecimal(amount), Currency.CHF)
        fun eur(amount: String): Money = Money(BigDecimal(amount), Currency.EUR)
    }
}
