package cat.subi.itaca.health.application

import cat.subi.itaca.TestcontainersConfiguration
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The semantic (AI) normalization maps an un-normalizable name to a canonical code.
 * The extraction and mapping calls hit the same stubbed endpoint, told apart by body.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class SemanticNormalizationIntegrationTest {
    @Autowired
    lateinit var service: LabReportService

    @Autowired
    lateinit var normalization: LabNormalizationService

    @Test
    fun `semantic renormalize links a name the dictionary cannot match`() {
        stubExtraction()
        stubMapping()

        val report = service.upload("rare.pdf", "fake-pdf-bytes".toByteArray())
        service.runExtraction(report.id)
        service.review(report.id, confirm = true)
        assertNull(
            service
                .detail(report.id)
                .results
                .single()
                .analyteCode,
            "deterministic matcher cannot place it",
        )

        val result = normalization.semanticRenormalize()
        assertEquals(
            "crp",
            service
                .detail(report.id)
                .results
                .single()
                .analyteCode,
            "the model mapped it to CRP",
        )
        assertEquals(1, result.changed)

        service.deleteReport(report.id)
    }

    private fun stubExtraction() {
        val json =
            """
            {"date":"2024-09-01","laboratory":"Lab","results":[
              {"analyte":"$RARE_NAME","value":3.1,"unit":"mg/L","refMin":0,"refMax":5}
            ]}
            """.trimIndent()
        stub("Extract every analyte", json)
    }

    private fun stubMapping() {
        stub("normalize laboratory result names", """{"mappings":[{"name":"$RARE_NAME","code":"crp"}]}""")
    }

    private fun stub(
        bodyMarker: String,
        payloadJson: String,
    ) {
        val escaped = payloadJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        wireMock.stubFor(
            post(urlEqualTo("/v1/messages")).withRequestBody(containing(bodyMarker)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "msg_01", "type": "message", "role": "assistant", "model": "claude-haiku-4-5",
                          "content": [{"type": "text", "text": "$escaped"}],
                          "stop_reason": "end_turn", "stop_sequence": null,
                          "usage": {"input_tokens": 100, "output_tokens": 40}
                        }
                        """.trimIndent(),
                    ),
            ),
        )
    }

    companion object {
        private const val RARE_NAME = "Proteina C reactiva ultra XYZ"
        private val wireMock = WireMockServer(wireMockConfig().dynamicPort()).apply { start() }
        private val storageDir = Files.createTempDirectory("itaca-semantic-test")

        @JvmStatic
        @AfterAll
        fun stop() = wireMock.stop()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.ai.anthropic.base-url") { wireMock.baseUrl() }
            registry.add("spring.ai.anthropic.api-key") { "test-key" }
            registry.add("itaca.storage.local-dir") { storageDir.toString() }
        }
    }
}
