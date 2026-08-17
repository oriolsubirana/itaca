package cat.subi.itaca.google.adapter.out

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The awaiting-reply heuristic against a stubbed Gmail API: a thread whose last message is from
 * someone else is surfaced; one whose last message is the user's own is filtered out.
 */
class GoogleGmailClientTest {
    private val server = WireMockServer(wireMockConfig().dynamicPort())

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.stop()

    private fun json(body: String) = aResponse().withHeader("Content-Type", "application/json").withBody(body)

    @Test
    fun `surfaces only threads whose last message is not the user's`() {
        server.stubFor(get(urlPathEqualTo("/users/me/profile")).willReturn(json("""{"emailAddress":"me@x.com"}""")))
        server.stubFor(
            get(urlPathEqualTo("/users/me/threads"))
                .willReturn(json("""{"threads":[{"id":"t1"},{"id":"t2"}]}""")),
        )
        // t1: last message from someone else -> awaiting reply.
        server.stubFor(
            get(urlPathEqualTo("/users/me/threads/t1")).willReturn(
                json(
                    """
                    {"messages":[
                      {"internalDate":"1000","payload":{"headers":[
                        {"name":"From","value":"Emma García <emma@x.com>"},{"name":"Subject","value":"Catering"}]}}
                    ]}
                    """.trimIndent(),
                ),
            ),
        )
        // t2: last message is ours -> filtered out.
        server.stubFor(
            get(urlPathEqualTo("/users/me/threads/t2")).willReturn(
                json(
                    """
                    {"messages":[
                      {"internalDate":"500","payload":{"headers":[{"name":"From","value":"me@x.com"}]}},
                      {"internalDate":"2000","payload":{"headers":[{"name":"From","value":"me@x.com"}]}}
                    ]}
                    """.trimIndent(),
                ),
            ),
        )

        val client = GoogleGmailClient("http://localhost:${server.port()}")
        val threads = client.awaitingReply("tok", withinDays = 30, minAgeDays = 0, max = 8)

        assertEquals(1, threads.size)
        assertEquals("Emma García", threads.single().from)
        assertEquals("Catering", threads.single().subject)
    }
}
