package cat.subi.itaca.health.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class BristolScaleTest {
    @Test
    fun `accepts values between 1 and 7`() {
        assertEquals(4, BristolScale.of(4).value)
        assertEquals(1, BristolScale.of(1).value)
        assertEquals(7, BristolScale.of(7).value)
    }

    @Test
    fun `rejects values outside the scale`() {
        assertThrows<IllegalArgumentException> { BristolScale.of(0) }
        assertThrows<IllegalArgumentException> { BristolScale.of(8) }
    }
}
