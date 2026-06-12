package cat.subi.itaca.health.application

import cat.subi.itaca.TestcontainersConfiguration
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
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
import kotlin.test.assertTrue

/**
 * Full pipeline against a WireMock-stubbed Anthropic extraction: upload ->
 * extraction -> dictionary normalization -> review -> confirmed series.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class LabPipelineIntegrationTest {
    @Autowired
    lateinit var service: LabReportService

    @Autowired
    lateinit var queries: LabResultQueries

    @Test
    fun `extracts, normalizes against the dictionary, and feeds the series only after confirmation`() {
        val extractionJson =
            """
            {
              "date": "2026-05-20",
              "laboratory": "Unilabs Zürich",
              "results": [
                {"analyte": "Calprotectina fecal", "value": 184.0, "unit": "µg/g", "refMin": 0, "refMax": 50},
                {"analyte": "CRP", "value": 4.2, "unit": "mg/L", "refMin": 0, "refMax": 5},
                {"analyte": "Misteriosina", "value": 1.0, "unit": "u", "refMin": null, "refMax": null}
              ]
            }
            """.trimIndent()
        stubExtraction(extractionJson)

        val uploaded = service.upload("analitica-mayo.pdf", "fake-pdf-bytes".toByteArray())
        assertEquals("pending_review", uploaded.status)
        assertEquals("analitica-mayo.pdf", uploaded.filename)

        service.runExtraction(uploaded.id)

        val detail = service.detail(uploaded.id)
        assertEquals("2026-05-20", detail.report.date)
        assertEquals("Unilabs Zürich", detail.report.laboratory)
        assertEquals(3, detail.results.size)
        val calpro = detail.results.single { it.rawName == "Calprotectina fecal" }
        assertEquals("fecal_calprotectin", calpro.analyteCode, "must normalize via the dictionary")
        assertEquals(184.0, calpro.value)
        val unknown = detail.results.single { it.rawName == "Misteriosina" }
        assertNull(unknown.analyteCode, "unknown analytes stay unmatched")

        assertTrue(
            queries.seriesByCode("fecal_calprotectin")!!.points.isEmpty(),
            "pending reports must not feed the series",
        )

        service.review(uploaded.id, confirm = true)

        val series = queries.seriesByCode("fecal_calprotectin")!!
        assertEquals(1, series.points.size)
        assertEquals(184.0, series.points.single().value)
        assertEquals(50.0, series.points.single().refMax)

        val viaChat = queries.queryLabResults("calprotectina")
        assertEquals("fecal_calprotectin", viaChat?.code)
        assertEquals(1, viaChat?.points?.size)

        service.deleteReport(uploaded.id)
        assertTrue(
            queries.seriesByCode("fecal_calprotectin")!!.points.isEmpty(),
            "deleting the report must remove its results from the series",
        )
        assertTrue(runCatching { service.detail(uploaded.id) }.exceptionOrNull() is NoSuchElementException)
    }

    private fun stubExtraction(payloadJson: String) {
        val escaped =
            payloadJson
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
        wireMock.stubFor(
            post(urlEqualTo("/v1/messages")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "msg_lab_01",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-haiku-4-5",
                          "content": [{"type": "text", "text": "$escaped"}],
                          "stop_reason": "end_turn",
                          "stop_sequence": null,
                          "usage": {"input_tokens": 100, "output_tokens": 80}
                        }
                        """.trimIndent(),
                    ),
            ),
        )
    }

    companion object {
        private val wireMock = WireMockServer(wireMockConfig().dynamicPort()).apply { start() }
        private val storageDir = Files.createTempDirectory("itaca-lab-test")

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
