package cat.subi.itaca.google.application

import cat.subi.itaca.ingestion.DocumentInbox
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** Outcome of one folder poll. */
data class DriveSyncResult(
    val ingested: Int,
    val failed: List<String>,
)

/**
 * Watches a Drive folder: every poll lists it, and any file not handled before is downloaded and
 * handed to the ingestion pipeline (which classifies it as a lab report / bank statement and runs
 * the usual review gate). Idempotent via the seen-store; Google-native docs (Sheets/Docs) are
 * skipped since they can't be downloaded as bytes. No-op until the user connects Google and sets
 * the folder.
 */
@Service
class DriveSyncService(
    private val tokens: GoogleTokens,
    private val reader: DriveReader,
    private val seen: DriveSeenStore,
    private val inbox: DocumentInbox,
    @Value("\${GOOGLE_DRIVE_FOLDER_ID:}") private val folderId: String,
) {
    private val log = LoggerFactory.getLogger(DriveSyncService::class.java)

    fun sync(): DriveSyncResult {
        if (folderId.isBlank()) return EMPTY
        val token = tokens.accessToken() ?: return EMPTY
        val result = syncDriveFolder(token, folderId, reader, seen, inbox)
        if (result.ingested > 0 || result.failed.isNotEmpty()) {
            log.info("Drive sync: ingested {}, failed {}", result.ingested, result.failed)
        }
        return result
    }

    private companion object {
        val EMPTY = DriveSyncResult(0, emptyList())
    }
}

private const val GOOGLE_APPS_PREFIX = "application/vnd.google-apps"

/**
 * Pure poll step (no Spring/Google): for each unseen, downloadable file, download it, hand it to
 * the inbox, and mark it seen. A per-file failure is collected (not marked seen, so it retries).
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun syncDriveFolder(
    token: String,
    folderId: String,
    reader: DriveReader,
    seen: DriveSeenStore,
    inbox: DocumentInbox,
): DriveSyncResult {
    var ingested = 0
    val failed = mutableListOf<String>()
    val fresh = reader.listFolder(token, folderId).filterNot { seen.isSeen(it.id) }
    for (doc in fresh) {
        if (doc.mimeType.startsWith(GOOGLE_APPS_PREFIX)) {
            // Google-native docs can't be downloaded as bytes; mark them seen so we don't re-list.
            seen.markSeen(doc.id, doc.name, doc.mimeType)
        } else {
            try {
                inbox.receive("drive", doc.name, reader.download(token, doc.id))
                seen.markSeen(doc.id, doc.name, doc.mimeType)
                ingested++
            } catch (e: Exception) {
                failed.add(doc.name)
            }
        }
    }
    return DriveSyncResult(ingested, failed)
}
