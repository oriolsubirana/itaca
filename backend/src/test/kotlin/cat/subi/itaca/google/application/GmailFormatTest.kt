package cat.subi.itaca.google.application

import cat.subi.itaca.google.adapter.out.senderName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure formatting helpers for the inbox summary (no Gmail needed). */
class GmailFormatTest {
    @Test
    fun `senderName takes the display name when present`() {
        assertEquals("Emma García", senderName("Emma García <emma@x.com>"))
        assertEquals("Zaira", senderName("\"Zaira\" <z@x.com>"))
    }

    @Test
    fun `senderName falls back to the bare address`() {
        assertEquals("emma@x.com", senderName("emma@x.com"))
    }

    @Test
    fun `summarizeInbox lists sender, subject and age`() {
        val out = summarizeInbox(listOf(InboxThread("t1", "Catering 29.09", "Emma", "2026-06-23T08:00:00Z", 2)))
        assertTrue(out.contains("Emma"))
        assertTrue(out.contains("Catering"))
        assertTrue(out.contains("hace 2 días"))
    }

    @Test
    fun `summarizeInbox reports an empty inbox`() {
        assertEquals("No tienes correos pendientes de responder.", summarizeInbox(emptyList()))
    }
}
