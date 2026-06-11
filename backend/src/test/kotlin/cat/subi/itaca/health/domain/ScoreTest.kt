package cat.subi.itaca.health.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ScoreTest {
    @Test
    fun `accepts values between 0 and 10`() {
        assertEquals(0, Score.of(0).value)
        assertEquals(5, Score.of(5).value)
        assertEquals(10, Score.of(10).value)
    }

    @Test
    fun `rejects values outside the scale`() {
        assertThrows<IllegalArgumentException> { Score.of(-1) }
        assertThrows<IllegalArgumentException> { Score.of(11) }
    }
}
