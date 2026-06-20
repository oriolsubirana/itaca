package cat.subi.itaca.shared.security

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleEmailAllowlistTest {
    @Test
    fun `allows the configured email regardless of case`() {
        assertTrue(isAllowed("Oriol@Example.com", "oriol@example.com"))
    }

    @Test
    fun `rejects any other email`() {
        assertFalse(isAllowed("intruder@example.com", "oriol@example.com"))
    }

    @Test
    fun `rejects a null email when an allowlist is set`() {
        assertFalse(isAllowed(null, "oriol@example.com"))
    }

    @Test
    fun `allows anyone when the allowlist is empty`() {
        assertTrue(isAllowed("anyone@example.com", ""))
    }
}
