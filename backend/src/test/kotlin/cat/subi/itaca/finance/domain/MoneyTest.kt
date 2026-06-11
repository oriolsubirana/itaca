package cat.subi.itaca.finance.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MoneyTest {

    @Test
    fun `suma importes de la misma divisa`() {
        assertEquals(Money.chf("150.50"), Money.chf("100.00") + Money.chf("50.50"))
    }

    @Test
    fun `rechaza sumar divisas distintas sin conversion explicita`() {
        assertThrows<IllegalArgumentException> { Money.chf("100") + Money.eur("100") }
    }
}
