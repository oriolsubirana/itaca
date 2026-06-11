package cat.subi.itaca.finance.domain

import java.math.BigDecimal

enum class Currency { CHF, EUR }

/**
 * Importe monetario con divisa explícita. Las operaciones entre divisas
 * distintas requieren conversión previa (no implícita).
 */
data class Money(val amount: BigDecimal, val currency: Currency) {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "No se pueden sumar importes en divisas distintas: $currency + ${other.currency}"
        }
        return Money(amount + other.amount, currency)
    }

    companion object {
        fun chf(amount: String): Money = Money(BigDecimal(amount), Currency.CHF)
        fun eur(amount: String): Money = Money(BigDecimal(amount), Currency.EUR)
    }
}
