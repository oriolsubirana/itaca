package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepsTest {

    @Test
    fun `se crea a partir de un numero de repeticiones`() {
        assertEquals(12, Reps.of(12).value)
    }

    @Test
    fun `rechaza cero repeticiones`() {
        assertThrows<IllegalArgumentException> { Reps.of(0) }
    }

    @Test
    fun `rechaza repeticiones negativas`() {
        assertThrows<IllegalArgumentException> { Reps.of(-3) }
    }

    @Test
    fun `rechaza valores absurdos`() {
        assertThrows<IllegalArgumentException> { Reps.of(201) }
    }

    @Test
    fun `sabe si supera un objetivo con margen`() {
        val target = Reps.of(8)
        assertTrue(Reps.of(10).exceedsWithMargin(target))
        assertFalse(Reps.of(9).exceedsWithMargin(target))
        assertFalse(Reps.of(8).exceedsWithMargin(target))
    }
}
