// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.google.adapter.`in`.rest

import cat.subi.itaca.google.application.GoogleCalendarService
import cat.subi.itaca.google.application.GoogleGmailService
import cat.subi.itaca.google.application.InboxThread
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** One upcoming event for the agenda glance (allDay tells the UI to skip the time). */
data class AgendaEvent(
    val summary: String,
    val start: String,
    val allDay: Boolean,
    val location: String?,
)

/**
 * Read-only Google data for the Home agenda glance: upcoming calendar events and the emails awaiting
 * a reply. The chat's narrative summary uses the query_calendar / query_inbox tools instead; this is
 * the structured feed the dashboard renders.
 */
@RestController
class GoogleController(
    private val calendar: GoogleCalendarService,
    private val gmail: GoogleGmailService,
) {
    @GetMapping("/api/calendar")
    fun calendar(
        @RequestParam(required = false) days: Int?,
    ): List<AgendaEvent> = calendar.upcoming(days).map { AgendaEvent(it.summary, it.start, it.allDay, it.location) }

    @GetMapping("/api/inbox")
    fun inbox(
        @RequestParam(required = false) days: Int?,
    ): List<InboxThread> = gmail.awaitingReply(days)
}
