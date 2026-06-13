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
import kotlin.test.assertTrue

/**
 * Full clinical-document pipeline against a WireMock-stubbed extraction: upload ->
 * extraction -> review -> confirmed history consumed by the chat tool.
 */
@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@Import(TestcontainersConfiguration::class)
class MedicalDocumentPipelineIntegrationTest {
    @Autowired
    lateinit var service: MedicalDocumentService

    @Autowired
    lateinit var history: MedicalHistoryQueries

    @Test
    fun `extracts facts, gates on review, and feeds the medical history`() {
        stubExtraction(
            """
            {
              "date": "2018-02-21",
              "type": "Urgencias",
              "provider": "Ruiz Gemar, Ines",
              "center": "Hospital de Sabadell",
              "category": "ibd",
              "fullText": "Paciente con antecedentes de proctosigmoiditis ulcerosa diagnosticado en 2007.",
              "diagnoses": [
                {"code": "J0390", "label": "Amigdalitis aguda", "date": "2018-02-21"},
                {"code": null, "label": "Proctosigmoiditis ulcerosa", "date": "2007-01-01"}
              ],
              "medications": [
                {"name": "Amoxicilina", "dose": "500mg", "schedule": "1c/8h", "duration": "7 días", "reason": null}
              ]
            }
            """.trimIndent(),
        )

        val uploaded = service.upload("alta-urgencias.pdf", "fake-pdf-bytes".toByteArray())
        assertEquals("pending_review", uploaded.status)
        assertTrue(uploaded.extracting)

        service.runExtraction(uploaded.id)
        val detail = service.detail(uploaded.id)
        assertEquals("2018-02-21", detail.document.date)
        assertEquals("Urgencias", detail.document.type)
        assertEquals("ibd", detail.document.category, "Claude's classification is stored")
        assertEquals(false, detail.document.extracting)
        assertEquals(2, detail.diagnoses.size)
        assertEquals("Amigdalitis aguda", detail.diagnoses.first().label)
        assertEquals("Amoxicilina", detail.medications.single().name)

        assertTrue(
            history.queryMedicalHistory("colitis").encounters.isEmpty(),
            "pending documents must not feed the history",
        )

        service.review(uploaded.id, confirm = true)

        val viaChat = history.queryMedicalHistory("proctosigmoiditis").encounters
        assertEquals(1, viaChat.size)
        assertEquals("2018-02-21", viaChat.single().date)
        assertTrue(viaChat.single().diagnoses.any { it.contains("Proctosigmoiditis") })
        assertTrue(viaChat.single().medications.any { it.contains("Amoxicilina") })

        val byDrug = history.queryMedicalHistory("amoxicilina").encounters
        assertEquals(1, byDrug.size)

        service.deleteDocument(uploaded.id)
        assertTrue(
            history.queryMedicalHistory("").encounters.isEmpty(),
            "deleting the document removes it from the history",
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
                          "id": "msg_doc_01",
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
        private val storageDir = Files.createTempDirectory("itaca-medical-test")

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
