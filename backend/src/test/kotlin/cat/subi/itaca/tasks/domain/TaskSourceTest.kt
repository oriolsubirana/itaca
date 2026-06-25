package cat.subi.itaca.tasks.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Where a task came from. Parsing is lenient: tagging must never block creating a task. */
class TaskSourceTest {
    @Test
    fun `parses known sources case-insensitively`() {
        assertEquals(TaskSource.CHAT, TaskSource.from("chat"))
        assertEquals(TaskSource.EMAIL, TaskSource.from("EMAIL"))
        assertEquals(TaskSource.EMAIL, TaskSource.from("gmail"))
    }

    @Test
    fun `falls back to manual for null, blank or unknown`() {
        assertEquals(TaskSource.MANUAL, TaskSource.from(null))
        assertEquals(TaskSource.MANUAL, TaskSource.from(""))
        assertEquals(TaskSource.MANUAL, TaskSource.from("nonsense"))
    }

    @Test
    fun `wire form is the lowercase name`() {
        assertEquals("manual", TaskSource.MANUAL.wire)
        assertEquals("email", TaskSource.EMAIL.wire)
    }
}
