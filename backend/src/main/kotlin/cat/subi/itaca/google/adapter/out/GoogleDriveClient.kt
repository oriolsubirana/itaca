package cat.subi.itaca.google.adapter.out

import cat.subi.itaca.google.application.DriveDoc
import cat.subi.itaca.google.application.DriveReader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/** Google's files.list payload (only the fields we use; Jackson 3 ignores the rest). */
class DriveListResponse {
    var files: List<DriveFileItem> = emptyList()
    var nextPageToken: String? = null
}

class DriveFileItem {
    var id: String? = null
    var name: String? = null
    var mimeType: String? = null
}

/** Reads the Drive folder over REST. Base URL is injectable so tests can point it at a stub. */
@Component
class GoogleDriveClient(
    @Value("\${itaca.google.drive-base:https://www.googleapis.com/drive/v3}") base: String,
) : DriveReader {
    private val api = RestClient.create(base)
    private val log = LoggerFactory.getLogger(GoogleDriveClient::class.java)

    override fun listFolder(
        accessToken: String,
        folderId: String,
    ): List<DriveDoc> {
        val q = "'$folderId' in parents and trashed = false"
        log.info("Drive listFolder: folderId=[{}] q=[{}]", folderId, q)
        probeFolder(accessToken, folderId)
        val docs = mutableListOf<DriveDoc>()
        var pageToken: String? = null
        var pages = 0
        do {
            val token = pageToken
            val response =
                try {
                    api
                        .get()
                        .uri { b ->
                            b
                                .path("/files")
                                .queryParam("q", q)
                                .queryParam("fields", "nextPageToken,files(id,name,mimeType)")
                                .queryParam("orderBy", "createdTime")
                                .queryParam("pageSize", PAGE_SIZE)
                                // Also reach folders in Shared Drives / shared-with-me, not just My Drive.
                                .queryParam("supportsAllDrives", "true")
                                .queryParam("includeItemsFromAllDrives", "true")
                            if (token != null) b.queryParam("pageToken", token)
                            b.build()
                        }.header("Authorization", "Bearer $accessToken")
                        .retrieve()
                        .body(DriveListResponse::class.java)
                } catch (e: RestClientResponseException) {
                    log.warn("Drive listFolder HTTP {}: {}", e.statusCode, e.responseBodyAsString)
                    throw e
                }
            response?.files.orEmpty().mapNotNullTo(docs) { f ->
                val id = f.id ?: return@mapNotNullTo null
                log.info("Drive listFolder item: id=[{}] name=[{}] mime=[{}]", f.id, f.name, f.mimeType)
                DriveDoc(id, f.name ?: id, f.mimeType ?: "application/octet-stream")
            }
            pageToken = response?.nextPageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        return docs
    }

    /**
     * Diagnostic: resolve the folder by id (files.get) before listing it. Isolates "the token can't
     * see this id at all" from "the list query is the problem", and reveals driveId (null = My Drive)
     * and ownedByMe so we see the folder as the API does. Failures are logged, never thrown.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun probeFolder(
        accessToken: String,
        folderId: String,
    ) {
        try {
            val meta =
                api
                    .get()
                    .uri { b ->
                        b
                            .path("/files/{id}")
                            .queryParam("fields", "id,name,mimeType,driveId,ownedByMe,trashed,parents")
                            .queryParam("supportsAllDrives", "true")
                            .build(folderId)
                    }.header("Authorization", "Bearer $accessToken")
                    .retrieve()
                    .body(String::class.java)
            log.info("Drive folder probe OK: {}", meta)
        } catch (e: RestClientResponseException) {
            log.warn("Drive folder probe HTTP {}: {}", e.statusCode, e.responseBodyAsString)
        } catch (e: Exception) {
            log.warn("Drive folder probe failed: {}", e.toString())
        }
        probeVisibleFolders(accessToken)
    }

    /**
     * Diagnostic: list the folders this token CAN see (id, name, ownedByMe). If the configured id
     * isn't visible, this surfaces the real "itaca" folder id so the secret can be corrected, and
     * confirms whether the stored account is the one that owns it. Failures are logged, never thrown.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun probeVisibleFolders(accessToken: String) {
        try {
            val folders =
                api
                    .get()
                    .uri { b ->
                        b
                            .path("/files")
                            .queryParam("q", "mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                            .queryParam("fields", "files(id,name,ownedByMe)")
                            .queryParam("pageSize", PAGE_SIZE)
                            .queryParam("supportsAllDrives", "true")
                            .queryParam("includeItemsFromAllDrives", "true")
                            .build()
                    }.header("Authorization", "Bearer $accessToken")
                    .retrieve()
                    .body(String::class.java)
            log.info("Drive visible folders for this token: {}", folders)
        } catch (e: RestClientResponseException) {
            log.warn("Drive visible-folders probe HTTP {}: {}", e.statusCode, e.responseBodyAsString)
        } catch (e: Exception) {
            log.warn("Drive visible-folders probe failed: {}", e.toString())
        }
    }

    override fun download(
        accessToken: String,
        fileId: String,
    ): ByteArray {
        log.info("Drive download: fileId=[{}]", fileId)
        return try {
            api
                .get()
                .uri { b ->
                    b
                        .path("/files/{id}")
                        .queryParam("alt", "media")
                        .queryParam("supportsAllDrives", "true")
                        .build(fileId)
                }.header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(ByteArray::class.java) ?: ByteArray(0)
        } catch (e: RestClientResponseException) {
            log.warn("Drive download HTTP {} for fileId=[{}]: {}", e.statusCode, fileId, e.responseBodyAsString)
            throw e
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20 // safety cap: up to ~2000 files per poll
    }
}
