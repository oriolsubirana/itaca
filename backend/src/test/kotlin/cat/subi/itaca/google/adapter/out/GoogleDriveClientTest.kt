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

/** Parses Drive's files.list payload and downloads bytes against a WireMock-stubbed Drive API. */
class GoogleDriveClientTest {
    private val server = WireMockServer(wireMockConfig().dynamicPort())

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.stop()

    @Test
    fun `lists the folder, ignoring unknown fields`() {
        server.stubFor(
            get(urlPathEqualTo("/files")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "kind": "drive#fileList",
                          "files": [
                            { "id": "f1", "name": "analitica.pdf", "mimeType": "application/pdf" },
                            { "id": "f2", "name": "extracto.csv", "mimeType": "text/csv" }
                          ]
                        }
                        """.trimIndent(),
                    ),
            ),
        )

        val client = GoogleDriveClient("http://localhost:${server.port()}")
        val files = client.listFolder("tok", "folder-123")

        assertEquals(2, files.size)
        assertEquals("analitica.pdf", files[0].name)
        assertEquals("text/csv", files[1].mimeType)
    }

    @Test
    fun `downloads file bytes with the bearer token`() {
        server.stubFor(
            get(urlPathEqualTo("/files/f1")).willReturn(
                aResponse().withHeader("Content-Type", "application/pdf").withBody("PDF-BYTES"),
            ),
        )

        val client = GoogleDriveClient("http://localhost:${server.port()}")
        val bytes = client.download("my-token", "f1")

        assertEquals("PDF-BYTES", String(bytes))
        server.verify(
            getRequestedFor(urlPathEqualTo("/files/f1")).withHeader("Authorization", equalTo("Bearer my-token")),
        )
    }
}
