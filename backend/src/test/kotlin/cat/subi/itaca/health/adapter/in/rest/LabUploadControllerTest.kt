// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.health.adapter.`in`.rest

import cat.subi.itaca.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files

@SpringBootTest(properties = ["jobrunr.background-job-server.enabled=false"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class LabUploadControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `accepts several PDFs in one request, one report per file`() {
        val january = MockMultipartFile("files", "analitica-enero.pdf", "application/pdf", "pdf-1".toByteArray())
        val february = MockMultipartFile("files", "analitica-febrero.pdf", "application/pdf", "pdf-2".toByteArray())

        mockMvc
            .perform(multipart("/api/health/lab-reports").file(january).file(february))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].status").value("pending_review"))
            .andExpect(jsonPath("$[1].status").value("pending_review"))
    }

    @Test
    fun `rejects uploads with an empty file`() {
        val empty = MockMultipartFile("files", "vacio.pdf", "application/pdf", ByteArray(0))

        mockMvc
            .perform(multipart("/api/health/lab-reports").file(empty))
            .andExpect(status().isBadRequest)
    }

    companion object {
        private val storageDir = Files.createTempDirectory("itaca-upload-test")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("itaca.storage.local-dir") { storageDir.toString() }
        }
    }
}
