package cat.subi.itaca.health.adapter.out.storage

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
 * Port for storing uploaded lab report files. Returns the storage path used
 * to retrieve the content later.
 */
interface LabFileStorage {
    fun store(
        filename: String,
        content: ByteArray,
    ): String

    fun load(path: String): ByteArray
}

@Configuration
class LabFileStorageConfig {
    @Bean
    @ConditionalOnProperty("supabase.url")
    fun supabaseLabFileStorage(
        @Value("\${supabase.url}") supabaseUrl: String,
        @Value("\${supabase.service-key}") serviceKey: String,
        @Value("\${supabase.bucket:lab-reports}") bucket: String,
    ): LabFileStorage = SupabaseLabFileStorage(supabaseUrl, serviceKey, bucket)

    @Bean
    @ConditionalOnMissingBean(LabFileStorage::class)
    fun localLabFileStorage(
        @Value("\${itaca.storage.local-dir:./data/lab-reports}") localDir: String,
    ): LabFileStorage = LocalLabFileStorage(localDir)
}

/** Default storage for local development: plain files under a local directory. */
class LocalLabFileStorage(
    localDir: String,
) : LabFileStorage {
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
class SupabaseLabFileStorage(
    supabaseUrl: String,
    private val serviceKey: String,
    private val bucket: String,
) : LabFileStorage {
    private val log = LoggerFactory.getLogger(SupabaseLabFileStorage::class.java)
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
        log.info("Stored lab report in Supabase bucket {}: {}", bucket, path)
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
