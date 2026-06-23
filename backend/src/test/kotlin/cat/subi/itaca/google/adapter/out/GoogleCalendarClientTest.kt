package cat.subi.itaca.google.adapter.out

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parses Google's events.list payload against a WireMock-stubbed Calendar API. */
class GoogleCalendarClientTest {
    private val server = WireMockServer(wireMockConfig().dynamicPort())

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.stop()

    @Test
    fun `parses timed and all-day events, ignoring unknown fields`() {
        server.stubFor(
            get(urlPathEqualTo("/calendars/primary/events")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "kind": "calendar#events",
                          "items": [
                            {
                              "id": "abc",
                              "status": "confirmed",
                              "summary": "Cita gastro",
                              "location": "Hospital X",
                              "start": { "dateTime": "2026-06-24T17:00:00+02:00" },
                              "end": { "dateTime": "2026-06-24T17:30:00+02:00" }
                            },
                            {
                              "summary": "Analítica",
                              "start": { "date": "2026-06-26" }
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            ),
        )

        val client = GoogleCalendarClient("http://localhost:${server.port()}")
        val events = client.events("tok", "2026-06-22T00:00:00Z", "2026-07-22T00:00:00Z", 25)

        assertEquals(2, events.size)
        assertEquals(CalendarEvent("Cita gastro", "2026-06-24T17:00:00+02:00", false, "Hospital X"), events[0])
        assertEquals(CalendarEvent("Analítica", "2026-06-26", true, null), events[1])
    }

    @Test
    fun `sends the bearer token and returns empty when there are no items`() {
        server.stubFor(
            get(urlPathEqualTo("/calendars/primary/events")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody("""{"items": []}"""),
            ),
        )

        val client = GoogleCalendarClient("http://localhost:${server.port()}")
        val events = client.events("my-token", "2026-06-22T00:00:00Z", "2026-07-22T00:00:00Z", 25)

        assertTrue(events.isEmpty())
        server.verify(
            getRequestedFor(urlPathEqualTo("/calendars/primary/events"))
                .withHeader("Authorization", equalTo("Bearer my-token")),
        )
    }
}
