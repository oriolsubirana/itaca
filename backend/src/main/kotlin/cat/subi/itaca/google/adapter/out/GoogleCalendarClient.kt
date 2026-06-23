package cat.subi.itaca.google.adapter.out

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** One upcoming event, normalized. `start` is the raw RFC3339 dateTime, or yyyy-MM-dd if all-day. */
data class CalendarEvent(
    val summary: String,
    val start: String,
    val allDay: Boolean,
    val location: String?,
)

/** Google's events.list payload (only the fields we use; Jackson 3 ignores the rest). */
class CalendarListResponse {
    var items: List<CalendarItem> = emptyList()
}

class CalendarItem {
    var summary: String? = null
    var location: String? = null
    var start: CalendarTime? = null
}

class CalendarTime {
    var dateTime: String? = null
    var date: String? = null
}

/** Reads Google Calendar over REST. Base URL is injectable so tests can point it at a stub. */
@Component
class GoogleCalendarClient(
    @Value("\${itaca.google.calendar-base:https://www.googleapis.com/calendar/v3}") base: String,
) {
    private val api = RestClient.create(base)

    /** Events of the primary calendar between timeMin and timeMax (RFC3339), soonest first. */
    fun events(
        accessToken: String,
        timeMinIso: String,
        timeMaxIso: String,
        maxResults: Int,
    ): List<CalendarEvent> {
        val response =
            api
                .get()
                .uri { b ->
                    b
                        .path("/calendars/primary/events")
                        .queryParam("singleEvents", "true")
                        .queryParam("orderBy", "startTime")
                        .queryParam("timeMin", timeMinIso)
                        .queryParam("timeMax", timeMaxIso)
                        .queryParam("maxResults", maxResults)
                        .build()
                }.header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(CalendarListResponse::class.java)
        return response?.items.orEmpty().mapNotNull { it.toEvent() }
    }

    private fun CalendarItem.toEvent(): CalendarEvent? {
        val time = start ?: return null
        val startValue = time.dateTime ?: time.date ?: return null
        return CalendarEvent(
            summary = summary?.takeIf { it.isNotBlank() } ?: "(sin título)",
            start = startValue,
            allDay = time.dateTime == null,
            location = location?.takeIf { it.isNotBlank() },
        )
    }
}
