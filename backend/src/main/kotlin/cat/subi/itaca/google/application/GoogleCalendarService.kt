package cat.subi.itaca.google.application

import cat.subi.itaca.google.adapter.out.CalendarEvent
import cat.subi.itaca.google.adapter.out.GoogleCalendarClient
import cat.subi.itaca.shared.chat.ChatTools
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Exposes the user's Google Calendar to the chat (read-only). Auto-wired into the chat's
 * List<ChatTools>; needs the user to have signed in with Google (offline access) so a token is
 * stored. Describes the agenda only — it never creates or modifies events.
 */
@Service
class GoogleCalendarService(
    private val tokens: GoogleTokens,
    private val calendar: GoogleCalendarClient,
) : ChatTools {
    @Tool(
        name = "query_calendar",
        description =
            "Reads the user's upcoming Google Calendar events (medical appointments, plans, " +
                "trips...) within the next N days (default 7). Read-only; use it to answer about " +
                "their agenda or to factor appointments into training/nutrition suggestions.",
    )
    fun queryCalendar(
        @ToolParam(description = "How many days ahead to look (default 7, max 60)", required = false)
        days: Int?,
    ): String {
        val token = tokens.accessToken() ?: return "La agenda de Google no está conectada."
        val window = (days ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)
        val now = Instant.now()
        val events =
            calendar.events(
                token,
                now.toString(),
                now.plus(window.toLong(), ChronoUnit.DAYS).toString(),
                MAX_EVENTS,
            )
        return summarizeAgenda(events, window)
    }

    /** Structured upcoming events for the REST adapter (Home agenda glance); empty if not connected. */
    fun upcoming(days: Int?): List<CalendarEvent> {
        val token = tokens.accessToken() ?: return emptyList()
        val window = (days ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)
        val now = Instant.now()
        return calendar.events(
            token,
            now.toString(),
            now.plus(window.toLong(), ChronoUnit.DAYS).toString(),
            MAX_EVENTS,
        )
    }

    private companion object {
        const val DEFAULT_DAYS = 7
        const val MAX_DAYS = 60
        const val MAX_EVENTS = 25
    }
}

/** Pure formatting (testable without Google): a compact agenda the chat can rephrase in Spanish. */
fun summarizeAgenda(
    events: List<CalendarEvent>,
    days: Int,
): String {
    if (events.isEmpty()) return "No hay eventos en los próximos $days días."
    return events.joinToString("\n") { e ->
        val where = e.location?.let { " · $it" } ?: ""
        val day = if (e.allDay) "${e.start} (todo el día)" else e.start
        "- $day · ${e.summary}$where"
    }
}
