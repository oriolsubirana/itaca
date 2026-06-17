// `in` is a Kotlin keyword but the hexagonal convention is adapter/in/...
@file:Suppress("ktlint:standard:package-name")

package cat.subi.itaca.ingestion.adapter.`in`.rest

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
class IngestionControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `accepts several files in one request, one pending inbox entry per file`() {
        val csv = MockMultipartFile("files", "neon.csv", "text/csv", "\"Date\";\"Amount\"".toByteArray())
        val pdf = MockMultipartFile("files", "analitica.pdf", "application/pdf", "%PDF-1.7 fake".toByteArray())

        mockMvc
            .perform(multipart("/api/ingest").file(csv).file(pdf))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].status").value("pending"))
            .andExpect(jsonPath("$[0].type").value("csv"))
            .andExpect(jsonPath("$[1].type").value("pdf"))
    }

    @Test
    fun `rejects an upload with an empty file`() {
        val empty = MockMultipartFile("files", "vacio.pdf", "application/pdf", ByteArray(0))

        mockMvc
            .perform(multipart("/api/ingest").file(empty))
            .andExpect(status().isBadRequest)
    }

    companion object {
        private val storageDir = Files.createTempDirectory("itaca-ingest-upload-test")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("itaca.storage.local-dir") { storageDir.toString() }
        }
    }
}
