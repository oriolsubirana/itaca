package cat.subi.itaca.tasks.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A task needs a real title; surrounding whitespace is trimmed. */
class TaskTitleTest {
    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("Comprar pan", TaskTitle.of("  Comprar pan ").value)
    }

    @Test
    fun `rejects a blank title`() {
        assertFailsWith<IllegalArgumentException> { TaskTitle.of("") }
        assertFailsWith<IllegalArgumentException> { TaskTitle.of("   ") }
    }
}
