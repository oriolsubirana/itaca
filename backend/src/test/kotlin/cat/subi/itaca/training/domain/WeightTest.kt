package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WeightTest {
    @Test
    fun `creates from kilograms`() {
        val weight = Weight.ofKg(45.0)
        assertEquals("45 kg", weight.toString())
    }

    @Test
    fun `keeps decimals of fractional plates`() {
        val weight = Weight.ofKg(12.5)
        assertEquals("12.5 kg", weight.toString())
    }

    @Test
    fun `rejects negative weights`() {
        assertThrows<IllegalArgumentException> { Weight.ofKg(-5.0) }
    }

    @Test
    fun `rejects absurdly large weights`() {
        assertThrows<IllegalArgumentException> { Weight.ofKg(501.0) }
    }

    @Test
    fun `allows zero weight for bodyweight exercises`() {
        val bodyweight = Weight.ofKg(0.0)
        assertEquals("0 kg", bodyweight.toString())
    }

    @Test
    fun `progresses in standard steps of 2 and a half kg`() {
        val current = Weight.ofKg(45.0)
        assertEquals(Weight.ofKg(47.5), current.increasedByStandardStep())
    }

    @Test
    fun `two weights with the same value are equal`() {
        assertEquals(Weight.ofKg(50.0), Weight.ofKg(50.00))
    }
}
