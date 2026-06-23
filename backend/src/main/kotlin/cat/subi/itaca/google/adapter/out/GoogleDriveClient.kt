package cat.subi.itaca.google.adapter.out

import cat.subi.itaca.google.application.DriveDoc
import cat.subi.itaca.google.application.DriveReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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

    override fun listFolder(
        accessToken: String,
        folderId: String,
    ): List<DriveDoc> {
        val docs = mutableListOf<DriveDoc>()
        var pageToken: String? = null
        var pages = 0
        do {
            val token = pageToken
            val response =
                api
                    .get()
                    .uri { b ->
                        b
                            .path("/files")
                            .queryParam("q", "'$folderId' in parents and trashed = false")
                            .queryParam("fields", "nextPageToken,files(id,name,mimeType)")
                            .queryParam("orderBy", "createdTime")
                            .queryParam("pageSize", PAGE_SIZE)
                        if (token != null) b.queryParam("pageToken", token)
                        b.build()
                    }.header("Authorization", "Bearer $accessToken")
                    .retrieve()
                    .body(DriveListResponse::class.java)
            response?.files.orEmpty().mapNotNullTo(docs) { f ->
                val id = f.id ?: return@mapNotNullTo null
                DriveDoc(id, f.name ?: id, f.mimeType ?: "application/octet-stream")
            }
            pageToken = response?.nextPageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        return docs
    }

    override fun download(
        accessToken: String,
        fileId: String,
    ): ByteArray =
        api
            .get()
            .uri { b -> b.path("/files/{id}").queryParam("alt", "media").build(fileId) }
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(ByteArray::class.java) ?: ByteArray(0)

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20 // safety cap: up to ~2000 files per poll
    }
}
