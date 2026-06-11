package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WeightTest {

    @Test
    fun `se crea a partir de kilogramos`() {
        val weight = Weight.ofKg(45.0)
        assertEquals("45 kg", weight.toString())
    }

    @Test
    fun `conserva los decimales de los discos pequenos`() {
        val weight = Weight.ofKg(12.5)
        assertEquals("12.5 kg", weight.toString())
    }

    @Test
    fun `rechaza pesos negativos`() {
        assertThrows<IllegalArgumentException> { Weight.ofKg(-5.0) }
    }

    @Test
    fun `rechaza pesos desorbitados`() {
        assertThrows<IllegalArgumentException> { Weight.ofKg(501.0) }
    }

    @Test
    fun `permite peso cero para ejercicios sin lastre`() {
        val bodyweight = Weight.ofKg(0.0)
        assertEquals("0 kg", bodyweight.toString())
    }

    @Test
    fun `progresa en incrementos de 2 kg y medio`() {
        val current = Weight.ofKg(45.0)
        assertEquals(Weight.ofKg(47.5), current.increasedByStandardStep())
    }

    @Test
    fun `dos pesos con el mismo valor son iguales`() {
        assertEquals(Weight.ofKg(50.0), Weight.ofKg(50.00))
    }
}
