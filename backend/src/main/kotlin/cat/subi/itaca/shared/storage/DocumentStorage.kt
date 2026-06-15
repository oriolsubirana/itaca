package cat.subi.itaca.shared.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Shared port for storing uploaded documents (lab report PDFs, clinical documents,
 * ingested files...). Returns the storage path used to retrieve the content later.
 * Lives in the open `shared` module because several contexts (health, ingestion)
 * persist incoming files through it.
 */
interface DocumentStorage {
    fun store(
        filename: String,
        content: ByteArray,
    ): String

    fun load(path: String): ByteArray
}

/** A stored file's bytes plus its original filename (plain class: ByteArray needs no equals). */
class StoredFile(
    val filename: String,
    val content: ByteArray,
)

@Configuration
class DocumentStorageConfig {
    @Bean
    @ConditionalOnProperty("supabase.url")
    fun supabaseDocumentStorage(
        @Value("\${supabase.url}") supabaseUrl: String,
        @Value("\${supabase.service-key}") serviceKey: String,
        @Value("\${supabase.bucket:lab-reports}") bucket: String,
    ): DocumentStorage = SupabaseDocumentStorage(supabaseUrl, serviceKey, bucket)

    @Bean
    @ConditionalOnMissingBean(DocumentStorage::class)
    fun localDocumentStorage(
        @Value("\${itaca.storage.local-dir:./data/lab-reports}") localDir: String,
    ): DocumentStorage = LocalDocumentStorage(localDir)
}

/** Default storage for local development: plain files under a local directory. */
class LocalDocumentStorage(
    localDir: String,
) : DocumentStorage {
    private val root = Path.of(localDir)

    override fun store(
        filename: String,
        content: ByteArray,
    ): String {
        Files.createDirectories(root)
        val path = "${UUID.randomUUID()}-${sanitize(filename)}"
        Files.write(root.resolve(path), content)
        return path
    }

    override fun load(path: String): ByteArray = Files.readAllBytes(root.resolve(path))
}

/** Supabase Storage via its REST API (production). Active when supabase.url is set. */
class SupabaseDocumentStorage(
    supabaseUrl: String,
    private val serviceKey: String,
    private val bucket: String,
) : DocumentStorage {
    private val log = LoggerFactory.getLogger(SupabaseDocumentStorage::class.java)
    private val client = RestClient.builder().baseUrl("$supabaseUrl/storage/v1").build()

    override fun store(
        filename: String,
        content: ByteArray,
    ): String {
        val path = "${UUID.randomUUID()}-${sanitize(filename)}"
        client
            .post()
            .uri("/object/{bucket}/{path}", bucket, path)
            .header("Authorization", "Bearer $serviceKey")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(content)
            .retrieve()
            .toBodilessEntity()
        log.info("Stored document in Supabase bucket {}: {}", bucket, path)
        return path
    }

    override fun load(path: String): ByteArray =
        client
            .get()
            .uri("/object/{bucket}/{path}", bucket, path)
            .header("Authorization", "Bearer $serviceKey")
            .retrieve()
            .body(ByteArray::class.java)!!
}

private fun sanitize(filename: String): String = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
