package cat.subi.itaca.google.application

import cat.subi.itaca.google.adapter.out.CalendarEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgendaSummaryTest {
    @Test
    fun `reports an empty window with the day count`() {
        assertEquals("No hay eventos en los próximos 7 días.", summarizeAgenda(emptyList(), 7))
    }

    @Test
    fun `lists timed and all-day events with location`() {
        val events =
            listOf(
                CalendarEvent("Cita gastro", "2026-06-24T17:00:00+02:00", allDay = false, location = "Hospital X"),
                CalendarEvent("Analítica", "2026-06-26", allDay = true, location = null),
            )

        val out = summarizeAgenda(events, 7)

        assertTrue(out.contains("- 2026-06-24T17:00:00+02:00 · Cita gastro · Hospital X"))
        assertTrue(out.contains("- 2026-06-26 (todo el día) · Analítica"))
    }
}
