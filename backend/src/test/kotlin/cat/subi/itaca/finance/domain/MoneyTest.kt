package cat.subi.itaca.finance.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MoneyTest {

    @Test
    fun `adds amounts of the same currency`() {
        assertEquals(Money.chf("150.50"), Money.chf("100.00") + Money.chf("50.50"))
    }

    @Test
    fun `rejects adding different currencies without explicit conversion`() {
        assertThrows<IllegalArgumentException> { Money.chf("100") + Money.eur("100") }
    }
}
