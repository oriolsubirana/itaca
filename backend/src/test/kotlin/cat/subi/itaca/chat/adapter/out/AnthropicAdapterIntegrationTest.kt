package cat.subi.itaca.chat.adapter.out

import cat.subi.itaca.TestcontainersConfiguration
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals

/**
 * Verifies the real HTTP wiring of Spring AI's Anthropic client (properties,
 * paths, payload parsing) against a WireMock stub of POST /v1/messages.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class AnthropicAdapterIntegrationTest {
    @Autowired
    lateinit var chatClient: ChatClient

    @Test
    fun `sends a prompt to the Anthropic messages endpoint and parses the reply`() {
        wireMock.stubFor(
            post(urlEqualTo("/v1/messages")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "msg_test_01",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-sonnet-4-6",
                          "content": [{"type": "text", "text": "¡Hola, Oriol!"}],
                          "stop_reason": "end_turn",
                          "stop_sequence": null,
                          "usage": {"input_tokens": 10, "output_tokens": 8}
                        }
                        """.trimIndent(),
                    ),
            ),
        )

        val reply =
            chatClient
                .prompt()
                .user("Hola")
                .call()
                .content()

        assertEquals("¡Hola, Oriol!", reply)
    }

    companion object {
        private val wireMock =
            WireMockServer(wireMockConfig().dynamicPort()).apply { start() }

        @JvmStatic
        @AfterAll
        fun stop() = wireMock.stop()

        @JvmStatic
        @DynamicPropertySource
        fun anthropicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.ai.anthropic.base-url") { wireMock.baseUrl() }
            registry.add("spring.ai.anthropic.api-key") { "test-key" }
        }
    }
}
