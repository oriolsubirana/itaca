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
                {"analyte": "Misteriosina", "value": 1.0, "unit": "u", "refMin": null, "refMax": null},
                {"analyte": "Blut im Urin", "value": null, "textValue": "++++", "unit": null, "refMin": null, "refMax": null},
                {"analyte": "Hb", "value": null, "textValue": "normal", "unit": null, "refMin": null, "refMax": null},
                {"analyte": "Geisterwert", "value": null, "textValue": null, "unit": null, "refMin": null, "refMax": null}
              ]
            }
            """.trimIndent()
        stubExtraction(extractionJson)

        val uploaded = service.upload("analitica-mayo.pdf", "fake-pdf-bytes".toByteArray())
        assertEquals("pending_review", uploaded.status)
        assertEquals("analitica-mayo.pdf", uploaded.filename)

        service.runExtraction(uploaded.id)

        assertExtractedDetail(service.detail(uploaded.id))

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

        assertTrue(
            queries.seriesByCode("hemoglobin")!!.points.isEmpty(),
            "qualitative-only results must not feed the numeric series",
        )
        assertTrue(
            queries.analytesWithData().none { it.code == "hemoglobin" },
            "qualitative-only analytes must not appear in the chart selector",
        )

        service.deleteReport(uploaded.id)
        assertTrue(
            queries.seriesByCode("fecal_calprotectin")!!.points.isEmpty(),
            "deleting the report must remove its results from the series",
        )
        assertTrue(runCatching { service.detail(uploaded.id) }.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `lets the user correct, check off and re-extract results during review`() {
        stubExtraction(
            """{"date": "2026-06-01", "laboratory": "Unilabs", "results": [
                {"analyte": "CRP", "value": 42.0, "unit": "mg/L", "refMin": 0, "refMax": 5}
            ]}""",
        )
        val uploaded = service.upload("analitica-junio.pdf", "fake-pdf-bytes".toByteArray())
        assertTrue(uploaded.extracting, "a fresh upload reports its extraction as running")
        service.runExtraction(uploaded.id)
        val afterRun = service.detail(uploaded.id)
        assertEquals(false, afterRun.report.extracting, "the flag clears when the job finishes")
        val result = afterRun.results.single()

        val corrected = service.updateResult(result.id, LabResultUpdate(value = 4.2, reviewed = true))
        assertEquals(4.2, corrected.value)
        assertTrue(corrected.reviewed)

        val qualitative = service.updateResult(result.id, LabResultUpdate(textValue = "neg"))
        assertEquals("neg", qualitative.textValue, "a text value can be added during review")
        assertTrue(
            runCatching {
                service.updateResult(result.id, LabResultUpdate(textValue = ""))
            }.isSuccess,
            "clearing textValue is fine while a numeric value remains",
        )

        val requeued = service.prepareReextraction(uploaded.id)
        assertEquals("pending_review", requeued.status)
        assertTrue(requeued.extracting, "re-extraction flags the report as processing again")
        service.runExtraction(uploaded.id)
        val fresh = service.detail(uploaded.id).results.single()
        assertEquals(42.0, fresh.value, "re-extraction replaces edited rows")
        assertEquals(false, fresh.reviewed, "re-extraction resets review checkmarks")

        service.review(uploaded.id, confirm = true)
        assertTrue(
            runCatching { service.prepareReextraction(uploaded.id) }.exceptionOrNull() is IllegalArgumentException,
            "confirmed reports must not be re-extracted",
        )
        service.deleteReport(uploaded.id)
    }

    @Test
    fun `collapses duplicate reports of the same date into one series point`() {
        val json =
            """{"date": "2024-02-01", "laboratory": "labor team", "results": [
                {"analyte": "Leukozyten", "value": 6.3, "unit": "10^9/L", "refMin": 3.5, "refMax": 10}
            ]}"""
        stubExtraction(json)
        val ids =
            (1..3).map {
                val r = service.upload("leucos-$it.pdf", "fake-pdf-bytes".toByteArray())
                service.runExtraction(r.id)
                service.review(r.id, confirm = true)
                r.id
            }

        val series = queries.seriesByCode("leukocytes")!!
        assertEquals(1, series.points.size, "duplicate same-date reports must not stack points")
        assertEquals(6.3, series.points.single().value)

        ids.forEach { service.deleteReport(it) }
    }

    @Test
    fun `a cumulative report becomes a multi-date series from a single upload`() {
        val json =
            """{"date": "2024-03-01", "laboratory": "labor team w ag", "results": [
                {"analyte": "Leukozyten", "date": "2024-01-15", "value": 6.3, "unit": "10^9/L", "refMin": 3.5, "refMax": 10},
                {"analyte": "Leukozyten", "date": "2024-02-15", "value": 3.0, "unit": "10^9/L", "refMin": 3.5, "refMax": 10},
                {"analyte": "Leukozyten", "date": "2024-03-01", "value": 6.5, "unit": "10^9/L", "refMin": 3.5, "refMax": 10}
            ]}"""
        stubExtraction(json)
        val report = service.upload("kumulativ.pdf", "fake-pdf-bytes".toByteArray())
        service.runExtraction(report.id)
        service.review(report.id, confirm = true)

        val series = queries.seriesByCode("leukocytes")!!
        assertEquals(3, series.points.size, "each dated column is its own point")
        assertEquals(listOf("2024-01-15", "2024-02-15", "2024-03-01"), series.points.map { it.date })
        assertEquals(listOf(6.3, 3.0, 6.5), series.points.map { it.value })

        service.deleteReport(report.id)
    }

    private fun assertExtractedDetail(detail: LabReportDetail) {
        assertEquals("2026-05-20", detail.report.date)
        assertEquals("Unilabs Zürich", detail.report.laboratory)
        assertEquals(5, detail.results.size, "rows with neither value nor textValue are dropped")
        val calpro = detail.results.single { it.rawName == "Calprotectina fecal" }
        assertEquals("fecal_calprotectin", calpro.analyteCode, "must normalize via the dictionary")
        assertEquals(184.0, calpro.value)
        val unknown = detail.results.single { it.rawName == "Misteriosina" }
        assertNull(unknown.analyteCode, "unknown analytes stay unmatched")
        val qualitative = detail.results.single { it.rawName == "Blut im Urin" }
        assertNull(qualitative.value)
        assertEquals("++++", qualitative.textValue, "qualitative results keep their literal text")
        val namedQualitative = detail.results.single { it.rawName == "Hb" }
        assertNull(
            namedQualitative.analyteCode,
            "qualitative rows are not normalized even when the name matches a numeric analyte",
        )
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
