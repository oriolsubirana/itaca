package cat.subi.itaca.training.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepsTest {

    @Test
    fun `creates from a number of repetitions`() {
        assertEquals(12, Reps.of(12).value)
    }

    @Test
    fun `rejects zero reps`() {
        assertThrows<IllegalArgumentException> { Reps.of(0) }
    }

    @Test
    fun `rejects negative reps`() {
        assertThrows<IllegalArgumentException> { Reps.of(-3) }
    }

    @Test
    fun `rejects absurd values`() {
        assertThrows<IllegalArgumentException> { Reps.of(201) }
    }

    @Test
    fun `knows whether it exceeds a target with margin`() {
        val target = Reps.of(8)
        assertTrue(Reps.of(10).exceedsWithMargin(target))
        assertFalse(Reps.of(9).exceedsWithMargin(target))
        assertFalse(Reps.of(8).exceedsWithMargin(target))
    }
}
