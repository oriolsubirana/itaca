package cat.subi.itaca.google.application

/** A file found in the watched Drive folder. */
data class DriveDoc(
    val id: String,
    val name: String,
    val mimeType: String,
)

/** Port to read the Drive folder (list + download); implemented by the REST adapter. */
interface DriveReader {
    fun listFolder(
        accessToken: String,
        folderId: String,
    ): List<DriveDoc>

    fun download(
        accessToken: String,
        fileId: String,
    ): ByteArray
}

/** Remembers which Drive files the watcher already handed to ingestion (dedupe across polls). */
interface DriveSeenStore {
    fun isSeen(fileId: String): Boolean

    fun markSeen(
        fileId: String,
        name: String,
        mimeType: String,
    )
}
